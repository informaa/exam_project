package com.astanait.universityschedule.service;

import com.astanait.universityschedule.model.Room;
import com.astanait.universityschedule.model.ScheduleEntry; // Импорт
import com.astanait.universityschedule.repository.RoomRepository;
import com.astanait.universityschedule.repository.ScheduleEntryRepository; // Импорт
import org.springframework.beans.factory.annotation.Autowired; // Для конструктора
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // Для транзакций

import java.util.List;
import java.util.Optional;

@Service
public class RoomService {

    private final RoomRepository roomRepository;
    private final ScheduleEntryRepository scheduleEntryRepository; // Добавить

    @Autowired // Обновленный конструктор
    public RoomService(RoomRepository roomRepository, ScheduleEntryRepository scheduleEntryRepository) {
        this.roomRepository = roomRepository;
        this.scheduleEntryRepository = scheduleEntryRepository;
    }

    public List<Room> getAllRooms() {
        return roomRepository.findAll();
    }

    public Optional<Room> getRoomById(Long id) {
        return roomRepository.findById(id);
    }

    public Room saveRoom(Room room) {
        return roomRepository.save(room);
    }

    @Transactional
    public void deleteRoom(Long id) {
        Optional<Room> roomOptional = roomRepository.findById(id);
        if (roomOptional.isPresent()) {
            Room roomToDelete = roomOptional.get();

            List<ScheduleEntry> relatedScheduleEntries = scheduleEntryRepository.findByRoom(roomToDelete);
            if (!relatedScheduleEntries.isEmpty()) {
                scheduleEntryRepository.deleteAllInBatch(relatedScheduleEntries);
            }
            roomRepository.delete(roomToDelete);
        } else {
            System.err.println("Аудитория с ID " + id + " не найдена для удаления.");
        }
    }
}