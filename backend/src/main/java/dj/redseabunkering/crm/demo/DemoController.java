package dj.redseabunkering.crm.demo;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dj.redseabunkering.crm.common.CurrentUser;

@RestController
public class DemoController {

    private final DemoService demo;

    public DemoController(DemoService demo) {
        this.demo = demo;
    }

    @PostMapping("/api/admin/demo/reset")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(CurrentUser me) {
        demo.reset(me);
    }
}
