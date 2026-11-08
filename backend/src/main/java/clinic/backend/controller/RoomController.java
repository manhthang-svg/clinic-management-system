package clinic.backend.controller;

import clinic.backend.dto.response.RoomResponse;
import clinic.backend.service.RoomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/rooms")
public class RoomController {

    @Autowired
    private RoomService roomService;

    @GetMapping("/active-examination")
    public List<RoomResponse> getActiveExaminationRooms() {
        return roomService.getActiveExaminationRooms();
    }
}
