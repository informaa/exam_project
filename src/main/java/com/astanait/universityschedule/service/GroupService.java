package com.astanait.universityschedule.service;

import com.astanait.universityschedule.model.AppUser;
import com.astanait.universityschedule.model.Group;
import com.astanait.universityschedule.model.ScheduleEntry;
import com.astanait.universityschedule.repository.GroupRepository;
import com.astanait.universityschedule.repository.ScheduleEntryRepository;
import com.astanait.universityschedule.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class GroupService {

    private final GroupRepository groupRepository;
    private final ScheduleEntryRepository scheduleEntryRepository;
    private final UserRepository userRepository;

    @Autowired
    public GroupService(GroupRepository groupRepository,
                        ScheduleEntryRepository scheduleEntryRepository,
                        UserRepository userRepository) {
        this.groupRepository = groupRepository;
        this.scheduleEntryRepository = scheduleEntryRepository;
        this.userRepository = userRepository;
    }

    public List<Group> getAllGroups() {
        return groupRepository.findAll();
    }

    public Optional<Group> getGroupById(Long id) {
        return groupRepository.findById(id);
    }

    public Group saveGroup(Group group) {
        return groupRepository.save(group);
    }

    @Transactional
    public void deleteGroup(Long id) {
        Optional<Group> groupOptional = groupRepository.findById(id);
        if (groupOptional.isPresent()) {
            Group groupToDelete = groupOptional.get();

            List<AppUser> studentsInGroup = userRepository.findByStudentGroup(groupToDelete);
            for (AppUser student : studentsInGroup) {
                student.setStudentGroup(null);
                userRepository.save(student);
            }
            List<ScheduleEntry> relatedScheduleEntries = scheduleEntryRepository.findByGroup(groupToDelete);
            if (!relatedScheduleEntries.isEmpty()) {
                scheduleEntryRepository.deleteAllInBatch(relatedScheduleEntries); // Используйте deleteAllInBatch для эффективности
            }
            groupRepository.delete(groupToDelete);
        } else {
            System.err.println("Группа с ID " + id + " не найдена для удаления.");
        }
    }
}