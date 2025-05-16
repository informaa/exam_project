package com.astanait.universityschedule.repository;

import com.astanait.universityschedule.model.ExamScheduleEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExamScheduleEntryRepository extends JpaRepository<ExamScheduleEntry, Long> {

    List<ExamScheduleEntry> findByAcademicYearAndAcademicPeriodOrderByExamDateAscStartTimeAsc(Integer academicYear, Integer academicPeriod);
    List<ExamScheduleEntry> findByAcademicYearOrderByExamDateAscStartTimeAsc(Integer academicYear);

    List<ExamScheduleEntry> findByGroupNameAndAcademicYearAndAcademicPeriodOrderByExamDateAscStartTimeAsc(String groupName, Integer academicYear, Integer academicPeriod);
    List<ExamScheduleEntry> findByGroupNameAndAcademicYearOrderByExamDateAscStartTimeAsc(String groupName, Integer academicYear);
    List<ExamScheduleEntry> findByGroupNameOrderByExamDateAscStartTimeAsc(String groupName);
}