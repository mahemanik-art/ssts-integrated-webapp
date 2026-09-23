package org.sstamilschool.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PageController.class)
class PageControllerTest {
    @Autowired MockMvc mvc;

    @Test void homeShowsSchoolWelcome() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Sandy Springs Tamil School")));
    }

    @Test void informationalPagesAreMapped() throws Exception {
        for (String path : new String[]{"/about", "/team", "/calendar", "/contact"}) {
            mvc.perform(get(path)).andExpect(status().isOk()).andExpect(view().name("page"));
        }
    }
}
