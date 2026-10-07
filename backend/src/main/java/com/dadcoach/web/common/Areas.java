package com.dadcoach.web.common;

import com.dadcoach.auth.DashboardPrincipal;
import org.springframework.http.HttpStatus;

/** Area checks for the dashboard APIs: the session says who; these say which area they may use. */
public final class Areas {

    private Areas() {
    }

    public static long requireFather(DashboardPrincipal principal) {
        if (principal == null || !principal.isFather()) {
            throw new WebException(HttpStatus.FORBIDDEN, "NOT_A_FATHER", "the father area needs a father's session");
        }
        return principal.fatherId();
    }

    public static void requireStaff(DashboardPrincipal principal) {
        if (principal == null || !principal.isAdmin()) {
            throw new WebException(HttpStatus.FORBIDDEN, "NOT_STAFF", "the admin area is for the Dad Coach team");
        }
    }
}
