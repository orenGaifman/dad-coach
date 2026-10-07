package com.dadcoach.web.father;

import com.dadcoach.auth.DashboardPrincipal;
import com.dadcoach.auth.SessionCookies;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.web.common.Areas;
import com.dadcoach.web.common.WebException;
import com.dadcoach.web.training.TrainingService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The father's own area (/api/father/**): only ever his own data; someone else's object is a 404. */
@RestController
@RequestMapping("/api/father")
public class FatherAreaController {

    public record ConfirmRequest(@Size(max = 500) String note) {
    }

    public record ChildRequest(@Size(max = 100) String name, @Min(0) @Max(25) Integer age) {
    }

    public record NewChildRequest(@NotNull @Size(min = 1, max = 100) String name, @NotNull @Min(0) @Max(25) Integer age) {
    }

    public record SettingsRequest(@Size(max = 120) String name, @Size(max = 64) String timezone) {
    }

    public record DeleteRequest(@Size(max = 40) String confirmation) {
    }

    public record TrainingEvent(boolean completed) {
    }

    private final FatherRepository fathers;
    private final HomeService home;
    private final SessionsService sessions;
    private final ChildrenService children;
    private final ProgressService progress;
    private final SettingsService settings;
    private final TrainingService training;
    private final SessionCookies cookies;

    public FatherAreaController(FatherRepository fathers, HomeService home, SessionsService sessions, ChildrenService children,
                            ProgressService progress, SettingsService settings, TrainingService training,
                            SessionCookies cookies) {
        this.fathers = fathers;
        this.home = home;
        this.sessions = sessions;
        this.children = children;
        this.progress = progress;
        this.settings = settings;
        this.training = training;
        this.cookies = cookies;
    }

    @GetMapping("/home")
    public HomeView home(@AuthenticationPrincipal DashboardPrincipal p) {
        return home.home(me(p));
    }

    @GetMapping("/sessions")
    public SessionsService.SessionsView sessions(@AuthenticationPrincipal DashboardPrincipal p) {
        return sessions.list(me(p));
    }

    @PostMapping("/sessions/{id}/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirm(@AuthenticationPrincipal DashboardPrincipal p, @PathVariable UUID id,
                        @Valid @RequestBody(required = false) ConfirmRequest body) {
        sessions.confirm(me(p), id, body == null ? null : body.note());
    }

    @PostMapping("/sessions/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@AuthenticationPrincipal DashboardPrincipal p, @PathVariable UUID id) {
        sessions.cancel(me(p), id);
    }

    @GetMapping("/children")
    public List<ChildrenService.ChildView> children(@AuthenticationPrincipal DashboardPrincipal p) {
        return children.list(me(p));
    }

    @PostMapping("/children")
    @ResponseStatus(HttpStatus.CREATED)
    public ChildrenService.ChildView addChild(@AuthenticationPrincipal DashboardPrincipal p, @Valid @RequestBody NewChildRequest body) {
        return children.add(me(p), body.name(), body.age());
    }

    @PutMapping("/children/{id}")
    public ChildrenService.ChildView updateChild(@AuthenticationPrincipal DashboardPrincipal p, @PathVariable Long id,
                                                 @Valid @RequestBody ChildRequest body) {
        return children.update(me(p), id, body.name(), body.age());
    }

    @GetMapping("/progress")
    public ProgressService.ProgressView progress(@AuthenticationPrincipal DashboardPrincipal p) {
        return progress.progress(me(p));
    }

    @GetMapping("/settings")
    public SettingsService.SettingsView settings(@AuthenticationPrincipal DashboardPrincipal p) {
        return settings.view(me(p));
    }

    @PutMapping("/settings")
    public SettingsService.SettingsView updateSettings(@AuthenticationPrincipal DashboardPrincipal p,
                                                       @Valid @RequestBody SettingsRequest body) {
        return settings.update(me(p), body.name(), body.timezone());
    }

    @PostMapping("/calendar/connect")
    public Map<String, String> connectCalendar(@AuthenticationPrincipal DashboardPrincipal p) {
        return Map.of("url", settings.calendarConnectUrl(me(p)));
    }

    @PostMapping("/calendar/disconnect")
    public SettingsService.SettingsView disconnectCalendar(@AuthenticationPrincipal DashboardPrincipal p) {
        return settings.disconnectCalendar(me(p));
    }

    @PostMapping("/delete-my-data")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void deleteMyData(@AuthenticationPrincipal DashboardPrincipal p, @Valid @RequestBody DeleteRequest body,
                             HttpServletResponse response) {
        settings.deleteMyData(me(p), body.confirmation());
        cookies.clear(response);
    }

    @GetMapping("/training")
    public TrainingService.LibraryView training(@AuthenticationPrincipal DashboardPrincipal p) {
        return training.library(Areas.requireFather(p));
    }

    @PostMapping("/training/{slug}/progress")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void trainingProgress(@AuthenticationPrincipal DashboardPrincipal p, @PathVariable String slug,
                                 @RequestBody TrainingEvent body) {
        if (!training.record(Areas.requireFather(p), slug, body != null && body.completed())) {
            throw WebException.notFound();
        }
    }

    private Father me(DashboardPrincipal p) {
        return fathers.findById(Areas.requireFather(p)).orElseThrow(WebException::notFound);
    }
}
