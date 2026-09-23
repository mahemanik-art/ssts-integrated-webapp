package org.sstamilschool.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.sstamilschool.controller.PageController;

@WebMvcTest(PageController.class)
@Import(TestSecurityConfig.class)
class PageControllerTest {
    @Autowired MockMvc mvc;

    @Test void homeShowsSchoolWelcome() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Sandy Springs Tamil School")));
    }
}
