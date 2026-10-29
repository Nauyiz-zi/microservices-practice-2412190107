package com.zjgsu.zy.service;

import com.zjgsu.zy.dto.UserDtos;
import com.zjgsu.zy.model.CampusUser;
import com.zjgsu.zy.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserRepository users;
    public UserService(UserRepository users) { this.users = users; }

    @Transactional
    public UserDtos.View create(UserDtos.Create request) {
        String studentNo = request.studentNo().trim();
        if (users.existsByStudentNo(studentNo)) {
            throw new ApiException(HttpStatus.CONFLICT, "STUDENT_NO_EXISTS", "学号已经存在");
        }
        CampusUser user = new CampusUser();
        user.studentNo = studentNo;
        user.displayName = request.displayName().trim();
        user.enabled = true;
        return view(users.saveAndFlush(user));
    }

    @Transactional(readOnly = true)
    public UserDtos.View get(Long id) {
        return view(users.findById(id).orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在")));
    }

    private UserDtos.View view(CampusUser u) {
        return new UserDtos.View(u.id, u.studentNo, u.displayName, u.enabled, u.createdAt);
    }
}

