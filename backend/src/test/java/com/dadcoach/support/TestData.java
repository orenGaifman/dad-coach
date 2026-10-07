package com.dadcoach.support;

import com.dadcoach.channel.CommunicationEndpoint;
import com.dadcoach.channel.CommunicationEndpointRepository;
import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.child.ChildRepository;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.father.FatherStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Fathers, children and endpoints for tests; phones are +1999… test numbers (never real). */
@Component
public class TestData {

    private final FatherRepository fathers;
    private final ChildRepository children;
    private final CommunicationEndpointRepository endpoints;
    private final Clock clock;

    public TestData(FatherRepository fathers, ChildRepository children, CommunicationEndpointRepository endpoints, Clock clock) {
        this.fathers = fathers;
        this.children = children;
        this.endpoints = endpoints;
        this.clock = clock;
    }

    public Father father(String phone, String name, FatherStatus status) {
        Father f = new Father(phone);
        f.setDisplayName(name);
        f.setStatus(status);
        f.setTimezone("Asia/Jerusalem");
        return fathers.saveAndFlush(f);
    }

    public Father activeFather(String phone) {
        return father(phone, "דני", FatherStatus.ACTIVE);
    }

    public Child child(Father father, String name, int age) {
        Child c = new Child(father, name, LocalDate.now(clock).minusYears(age).minusDays(30));
        return children.saveAndFlush(c);
    }

    /** His WhatsApp endpoint with the 24-hour window open (he wrote an hour ago) or closed. */
    public CommunicationEndpoint endpoint(Father father, boolean windowOpen) {
        CommunicationEndpoint e = new CommunicationEndpoint(new UUID(0L, father.getId()), "WHATSAPP", father.getPhone());
        if (windowOpen) {
            e.setSessionOpensAt(clock.instant().minus(Duration.ofHours(1)));
            e.setSessionClosesAt(clock.instant().plus(Duration.ofHours(23)));
        }
        return endpoints.saveAndFlush(e);
    }
}
