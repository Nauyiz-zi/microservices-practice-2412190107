"""Run real services on isolated ports, test Feign calls, then stop only owned processes.
Usage: python verify-openfeign.py --root D:/microservices --output ./verification
Prerequisite: mvn package for student-service, course-service, enrollment-service.
"""
import argparse
import concurrent.futures
import json
import os
from pathlib import Path
import subprocess
import time
import urllib.request
import urllib.error

parser = argparse.ArgumentParser()
parser.add_argument('--root', default='D:/microservices')
parser.add_argument('--output', default='verification')
parser.add_argument('--base-port', type=int, default=28081)
args = parser.parse_args()
root = Path(args.root)
out = Path(args.output)
out.mkdir(parents=True, exist_ok=True)
processes, logs, results = {}, [], []
ports = {'course-service': args.base_port, 'student-service': args.base_port+1, 'enrollment-service': args.base_port+2}

def http(service, method, path, body=None):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(f'http://127.0.0.1:{ports[service]}{path}', data=data,
                                 headers={'Content-Type': 'application/json'}, method=method)
    start = time.monotonic()
    try:
        response = urllib.request.urlopen(req, timeout=12)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        raw = response.read().decode()
        return response.status, json.loads(raw) if raw else None, round(time.monotonic()-start, 3)

def check(label, actual, expected):
    code, body, elapsed = actual
    results.append(dict(test=label, expected=expected, status=code, body=body, seconds=elapsed, passed=code==expected))
    print(f'{label}: {code} expected {expected}', flush=True)
    assert code == expected, (label, actual)
    return body

def seats(course, expected):
    body = check(f'course {course} seats={expected}', http('course-service','GET',f'/api/courses/{course}'), 200)
    assert body['selectedCount'] == expected, body

def launch(service):
    log = (out/f'{service}.log').open('w', encoding='utf-8')
    logs.append(log)
    jar = root/service/'target'/f'{service}-0.1.0-SNAPSHOT.jar'
    command = ['java', '-jar', str(jar), f'--server.port={ports[service]}',
               '--spring.cloud.nacos.discovery.enabled=false', '--spring.jpa.show-sql=false']
    if service == 'enrollment-service':
        for dependency in ['student-service','course-service']:
            command += [f'--spring.cloud.discovery.client.simple.instances.{dependency}[0].uri=http://127.0.0.1:{ports[dependency]}']
    processes[service] = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT,
                                          creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0)

def wait_ready(service):
    deadline=time.monotonic()+90
    while time.monotonic()<deadline:
        if processes[service].poll() is not None:
            raise RuntimeError(f'{service} exited; inspect {out/service}.log')
        try:
            if http(service,'GET','/actuator/health')[0]==200:
                return
        except (OSError, urllib.error.URLError):
            pass
        time.sleep(.5)
    raise RuntimeError(f'{service} startup timeout')

try:
    # Refuse to interfere with a process already listening on our test ports.
    import socket
    for port in ports.values():
        with socket.socket() as s:
            s.bind(('127.0.0.1',port))
    for service in ports:
        launch(service)
    for service in ports:
        wait_ready(service)
    student = check('create student',http('student-service','POST','/api/students',{'studentNo':'F001','name':'Feign Test'}),201)['id']
    student2 = check('create student 2',http('student-service','POST','/api/students',{'studentNo':'F002','name':'Feign Test 2'}),201)['id']
    course = check('create course capacity=1',http('course-service','POST','/api/courses',{'code':'FEIGN','name':'OpenFeign','capacity':1}),201)['id']
    request={'studentId':student,'courseId':course}
    check('invalid input',http('enrollment-service','POST','/api/enrollments',{'studentId':0,'courseId':course}),400)
    check('student missing',http('enrollment-service','POST','/api/enrollments',{'studentId':99999,'courseId':course}),422)
    check('course missing',http('enrollment-service','POST','/api/enrollments',{'studentId':student,'courseId':99999}),422)
    enrollment=check('enroll via Feign',http('enrollment-service','POST','/api/enrollments',request),201)['id']
    seats(course,1)
    check('duplicate enrollment',http('enrollment-service','POST','/api/enrollments',request),409)
    seats(course,1)
    check('course full',http('enrollment-service','POST','/api/enrollments',{'studentId':student2,'courseId':course}),409)
    seats(course,1)
    check('drop via Feign',http('enrollment-service','DELETE',f'/api/enrollments/{enrollment}'),204)
    seats(course,0)
    check('dropped enrollment absent',http('enrollment-service','GET',f'/api/enrollments/{enrollment}'),404)
    check('repeat drop',http('enrollment-service','DELETE',f'/api/enrollments/{enrollment}'),404)
    enrollment=check('re-enroll after drop',http('enrollment-service','POST','/api/enrollments',request),201)['id']
    # Concurrent competing students must never oversubscribe capacity=1.
    other=check('create concurrent course',http('course-service','POST','/api/courses',{'code':'RACE','name':'Concurrency','capacity':1}),201)['id']
    with concurrent.futures.ThreadPoolExecutor(2) as pool:
        responses=list(pool.map(lambda sid:http('enrollment-service','POST','/api/enrollments',{'studentId':sid,'courseId':other}),[student,student2]))
    assert sorted(r[0] for r in responses)==[201,409],responses
    for i,r in enumerate(sorted(responses,key=lambda r:r[0])):
        check(f'concurrent enrollment {i}',r,[201,409][i])
    seats(other,1)
    processes['course-service'].terminate()
    processes['course-service'].wait(timeout=20)
    check('course stopped: enroll returns 503',http('enrollment-service','POST','/api/enrollments',{'studentId':student2,'courseId':course}),503)
    check('course stopped: drop returns 503',http('enrollment-service','DELETE',f'/api/enrollments/{enrollment}'),503)
    check('failed drop keeps enrollment',http('enrollment-service','GET',f'/api/enrollments/{enrollment}'),200)
    processes['student-service'].terminate()
    processes['student-service'].wait(timeout=20)
    check('student stopped: enroll returns 503',http('enrollment-service','POST','/api/enrollments',request),503)
finally:
    for process in processes.values():
        if process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=20)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
    for log in logs:
        log.close()
    (out/'results.json').write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding='utf-8')
print(f'PASS: {len(results)} HTTP assertions plus capacity assertions; logs in {out}',flush=True)
