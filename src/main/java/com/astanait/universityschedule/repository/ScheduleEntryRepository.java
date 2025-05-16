// репозиторий , который работает с данными расписания в базе данных
package com.astanait.universityschedule.repository;

import com.astanait.universityschedule.model.ScheduleEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ScheduleEntryRepository extends JpaRepository<ScheduleEntry, Long> {

    // Найти занятия в промежутке времени
    List<ScheduleEntry> findByStartTimeBetweenOrderByStartTimeAsc(LocalDateTime startOfWeek, LocalDateTime endOfWeek);
    // Поиск по году, семестру и неделе
    List<ScheduleEntry> findByAcademicYearAndSemesterAndWeekNumberOrderByStartTimeAsc(String academicYear, Integer semester, Integer weekNumber);
    // Поиск по году и семестру
    List<ScheduleEntry> findByAcademicYearAndSemesterOrderByStartTimeAsc(String academicYear, Integer semester);
    // Поиск только по году
    List<ScheduleEntry> findByAcademicYearOrderByStartTimeAsc(String academicYear);

    List<ScheduleEntry> findByGroupNameAndStartTimeBetweenOrderByStartTimeAsc(String groupName, LocalDateTime startOfWeek, LocalDateTime endOfWeek);

    // Для студентов и академических критериев
    List<ScheduleEntry> findByGroupNameAndAcademicYearAndSemesterAndWeekNumberOrderByStartTimeAsc(String groupName, String academicYear, Integer semester, Integer weekNumber);
    List<ScheduleEntry> findByGroupNameAndAcademicYearAndSemesterOrderByStartTimeAsc(String groupName, String academicYear, Integer semester);
    List<ScheduleEntry> findByGroupNameAndAcademicYearOrderByStartTimeAsc(String groupName, String academicYear);
    List<ScheduleEntry> findByGroupNameOrderByStartTimeAsc(String groupName);
}