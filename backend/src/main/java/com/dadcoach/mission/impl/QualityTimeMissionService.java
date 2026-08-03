package com.dadcoach.mission.impl;

import com.dadcoach.mission.Mission;
import com.dadcoach.mission.MissionService;
import com.dadcoach.mission.MissionType;
import com.dadcoach.qualitytime.QualityTime;
import com.dadcoach.qualitytime.QualityTimeRepository;
import com.dadcoach.qualitytime.QualityTimeService;
import com.dadcoach.qualitytime.QualityTimeStatus;
import com.dadcoach.qualitytime.dto.CompleteQualityTimeResult;
import com.dadcoach.qualitytime.dto.ScheduleQualityTimeResult;
import com.dadcoach.qualitytime.dto.UpcomingQualityTimeDto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * MVP implementation of {@link MissionService} for Quality Time missions.
 * 
 * <p>This service delegates all operations to {@link QualityTimeService} and adapts
 * the results to the {@link Mission} interface using {@link QualityTimeMissionAdapter}.</p>
 * 
 * <p><strong>Design Principles:</strong></p>
 * <ul>
 *   <li>Delegates to QualityTimeService for all actual operations</li>
 *   <li>Wraps QualityTime entities as Mission using the adapter pattern</li>
 *   <li>Returns {@link MissionType#QUALITY_TIME} from {@link #getSupportedType()}</li>
 * </ul>
 * 
 * <p><strong>Architecture Note:</strong></p>
 * This implementation enables the workflow engine to work with missions through the
 * abstract MissionService interface, supporting future extensibility to other mission
 * types without changing core workflow logic.
 * 
 * Requirements: 1.1 (MVP implementation)
 * 
 * @see MissionService
 * @see QualityTimeService
 * @see QualityTimeMissionAdapter
 */
@Service
@Transactional
public class QualityTimeMissionService implements MissionService {

    private static final Logger log = LoggerFactory.getLogger(QualityTimeMissionService.class);

    private final QualityTimeService qualityTimeService;
    private final QualityTimeRepository qualityTimeRepository;

    /**
     * Creates a new QualityTimeMissionService.
     * 
     * @param qualityTimeService    the Quality Time service to delegate operations to
     * @param qualityTimeRepository the Quality Time repository for direct queries
     */
    public QualityTimeMissionService(
            QualityTimeService qualityTimeService,
            QualityTimeRepository qualityTimeRepository) {
        this.qualityTimeService = qualityTimeService;
        this.qualityTimeRepository = qualityTimeRepository;
    }

    /**
     * {@inheritDoc}
     * 
     * <p>Delegates to {@link QualityTimeService#scheduleQualityTime} and wraps the result
     * as a Mission. Creates a Google Calendar event and database record.</p>
     */
    @Override
    public Mission schedule(Long fatherId, Long childId, Instant startTime, Duration duration) {
        log.debug("Scheduling Quality Time mission for father {} with child {}, starting at {}",
                fatherId, childId, startTime);

        ScheduleQualityTimeResult result = qualityTimeService.scheduleQualityTime(
                fatherId, childId, startTime, duration);

        // Fetch the created QualityTime entity to wrap as Mission
        QualityTime qualityTime = qualityTimeRepository.findById(result.qualityTimeId())
                .orElseThrow(() -> new IllegalStateException(
                        "Quality Time record not found after creation: " + result.qualityTimeId()));

        log.info("Scheduled Quality Time mission {} for father {} with child {}",
                qualityTime.getId(), fatherId, childId);

        return new QualityTimeMissionAdapter(qualityTime);
    }

    /**
     * {@inheritDoc}
     * 
     * <p>Delegates to {@link QualityTimeService#completeQualityTime}. Updates status,
     * increments streak, and checks belt milestones.</p>
     */
    @Override
    public Mission complete(UUID missionId, String notes) {
        log.debug("Completing Quality Time mission {} with notes: {}", missionId,
                notes != null ? notes.substring(0, Math.min(notes.length(), 50)) + "..." : "null");

        CompleteQualityTimeResult result = qualityTimeService.completeQualityTime(missionId, notes);

        // Fetch the completed QualityTime entity to wrap as Mission
        QualityTime qualityTime = qualityTimeRepository.findById(result.qualityTimeId())
                .orElseThrow(() -> new IllegalStateException(
                        "Quality Time record not found after completion: " + result.qualityTimeId()));

        log.info("Completed Quality Time mission {}. Streak: {}, Belt: {}",
                missionId, result.newStreak(), result.currentBelt());

        return new QualityTimeMissionAdapter(qualityTime);
    }

    /**
     * {@inheritDoc}
     * 
     * <p>Delegates to {@link QualityTimeService#cancelQualityTime}. Updates status
     * and deletes the Google Calendar event.</p>
     */
    @Override
    public void cancel(UUID missionId) {
        log.debug("Cancelling Quality Time mission {}", missionId);

        qualityTimeService.cancelQualityTime(missionId);

        log.info("Cancelled Quality Time mission {}", missionId);
    }

    /**
     * {@inheritDoc}
     * 
     * <p>Delegates to {@link QualityTimeService#getUpcomingQualityTime} and converts
     * the result to a Mission using the repository and adapter.</p>
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<Mission> getNextScheduled(Long fatherId) {
        log.debug("Getting next scheduled Quality Time mission for father {}", fatherId);

        Optional<UpcomingQualityTimeDto> upcoming = qualityTimeService.getUpcomingQualityTime(fatherId);

        if (upcoming.isEmpty()) {
            log.debug("No scheduled Quality Time found for father {}", fatherId);
            return Optional.empty();
        }

        // Fetch the full QualityTime entity to wrap as Mission
        QualityTime qualityTime = qualityTimeRepository.findById(upcoming.get().id())
                .orElseThrow(() -> new IllegalStateException(
                        "Quality Time record not found: " + upcoming.get().id()));

        return Optional.of(new QualityTimeMissionAdapter(qualityTime));
    }

    /**
     * {@inheritDoc}
     * 
     * <p>Queries the repository for recently completed Quality Time events and wraps
     * them as Missions. Ordered by completion time descending.</p>
     */
    @Override
    @Transactional(readOnly = true)
    public List<Mission> getRecentCompleted(Long fatherId, int limit) {
        log.debug("Getting {} recent completed Quality Time missions for father {}", limit, fatherId);

        // Query completed Quality Times, ordered by completedAt descending
        List<QualityTime> completedQualityTimes = qualityTimeRepository
                .findByFatherIdAndStatus(fatherId, QualityTimeStatus.COMPLETED);

        // Sort by completedAt descending and limit
        List<Mission> missions = completedQualityTimes.stream()
                .sorted((qt1, qt2) -> {
                    Instant t1 = qt1.getCompletedAt() != null ? qt1.getCompletedAt() : Instant.MIN;
                    Instant t2 = qt2.getCompletedAt() != null ? qt2.getCompletedAt() : Instant.MIN;
                    return t2.compareTo(t1); // Descending order
                })
                .limit(limit)
                .map(QualityTimeMissionAdapter::new)
                .collect(Collectors.toList());

        log.debug("Found {} recent completed Quality Time missions for father {}", missions.size(), fatherId);

        return missions;
    }

    /**
     * {@inheritDoc}
     * 
     * @return {@link MissionType#QUALITY_TIME}
     */
    @Override
    public MissionType getSupportedType() {
        return MissionType.QUALITY_TIME;
    }
}
