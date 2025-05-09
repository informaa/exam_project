package com.astanait.universityschedule.repository;

import com.astanait.universityschedule.model.ScheduleEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ScheduleEntryRepository extends JpaRepository<ScheduleEntry, Long> {

    List<ScheduleEntry> findByStartTimeBetweenOrderByStartTimeAsc(LocalDateTime startOfWeek, LocalDateTime endOfWeek);

    List<ScheduleEntry> findByAcademicYearAndSemesterAndWeekNumberOrderByStartTimeAsc(String academicYear, Integer semester, Integer weekNumber);
    List<ScheduleEntry> findByAcademicYearAndSemesterOrderByStartTimeAsc(String academicYear, Integer semester);
    List<ScheduleEntry> findByAcademicYearOrderByStartTimeAsc(String academicYear);
}