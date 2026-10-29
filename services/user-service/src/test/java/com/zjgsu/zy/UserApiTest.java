package com.zjgsu.zy;

import com.zjgsu.zy.dto.UserDtos;
import com.zjgsu.zy.repository.UserRepository;
import com.zjgsu.zy.service.UserService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserApiTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired UserService service;

    @BeforeEach void clean() { users.deleteAll(); }

    @Test void createAndRead() throws Exception {
        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentNo\":\"2412190107\",\"displayName\":\"字远\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.enabled").value(true));
        Long id = users.findAll().getFirst().id;
        mvc.perform(get("/api/users/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.studentNo").value("2412190107"));
    }

    @Test void duplicateStudentNoIs409() throws Exception {
        service.create(new UserDtos.Create("1001", "卖家"));
        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentNo\":\"1001\",\"displayName\":\"重复用户\"}"))
                .andExpect(status().isConflict());
        assertEquals(1, users.count());
    }

    @Test void missingUserIs404() throws Exception {
        mvc.perform(get("/api/users/999999")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test void invalidInputIs400() throws Exception {
        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentNo\":\"\",\"displayName\":\"\"}"))
                .andExpect(status().isBadRequest());
        assertEquals(0, users.count());
    }

    @Test void health() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}

