package com.astanait.universityschedule.repository;

import com.astanait.universityschedule.model.Group;
import com.astanait.universityschedule.model.Room;
import com.astanait.universityschedule.model.ScheduleEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScheduleEntryRepository extends JpaRepository<ScheduleEntry, Long> {
    void deleteByAcademicYearAndSemester(String academicYear, int semester);

    // Methods for fetching schedule based on new parameters (for viewing)
    List<ScheduleEntry> findByAcademicYearAndSemesterAndWeekInSemesterOrderByGroup_NameAscDayOfWeekAscLessonNumberAsc(String academicYear, int semester, int weekInSemester);
    List<ScheduleEntry> findByAcademicYearAndSemesterAndWeekInSemesterAndGroupOrderByDayOfWeekAscLessonNumberAsc(String academicYear, int semester, int weekInSemester, Group group);

    List<ScheduleEntry> findByGroup(Group group);
    List<ScheduleEntry> findByRoom(Room room);
}