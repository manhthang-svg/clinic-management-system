package clinic.backend.controller;

import clinic.backend.dto.request.CreateRoomRequest;
import clinic.backend.dto.response.RoomResponse;
import clinic.backend.service.RoomService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rooms")
public class RoomController {

    @Autowired
    private RoomService roomService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public RoomResponse createRoom(
            @Valid @RequestBody CreateRoomRequest request
    ) {
        return roomService.createRoom(request);
    }

    @org.springframework.web.bind.annotation.GetMapping("/active-examination")
    public java.util.List<RoomResponse> getActiveExaminationRooms() {
        return roomService.getActiveExaminationRooms();
    }
}
