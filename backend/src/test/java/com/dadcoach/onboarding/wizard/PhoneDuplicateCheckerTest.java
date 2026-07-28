package com.dadcoach.onboarding.wizard;

import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PhoneDuplicateCheckerTest {

    @Mock
    private FatherRepository fatherRepository;

    private PhoneDuplicateChecker checker;

    @BeforeEach
    void setUp() {
        checker = new PhoneDuplicateChecker(fatherRepository);
    }

    @Test
    void checkDuplicate_phoneNotRegistered_returnsSuccess() {
        String phone = "+972501234567";
        when(fatherRepository.findByPhone(phone)).thenReturn(Optional.empty());

        StepValidationResult result = checker.checkDuplicate(phone);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    void checkDuplicate_phoneAlreadyRegistered_returnsFailure() {
        String phone = "+972501234567";
        Father existingFather = new Father(phone);
        when(fatherRepository.findByPhone(phone)).thenReturn(Optional.of(existingFather));

        StepValidationResult result = checker.checkDuplicate(phone);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).fieldName()).isEqualTo("phone_number");
        assertThat(result.getErrors().get(0).errorCode()).isEqualTo("PHONE_REGISTERED");
    }

    @Test
    void checkDuplicate_nullPhone_returnsSuccess() {
        StepValidationResult result = checker.checkDuplicate(null);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void checkDuplicate_blankPhone_returnsSuccess() {
        StepValidationResult result = checker.checkDuplicate("  ");

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void checkDuplicate_phoneWithWhitespace_isTrimmed() {
        String phone = "+972501234567";
        when(fatherRepository.findByPhone(phone)).thenReturn(Optional.empty());

        StepValidationResult result = checker.checkDuplicate("  " + phone + "  ");

        assertThat(result.isValid()).isTrue();
    }
}
