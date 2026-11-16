package clinic.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.entity.ImagingResultImage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImagingResultImageRepository extends JpaRepository<ImagingResultImage, UUID> {

    /** UC-67: the images of one result in their printed order. */
    List<ImagingResultImage> findByImagingResultIdOrderByDisplayOrderAsc(UUID imagingResultId);

    Optional<ImagingResultImage> findByIdAndImagingResultId(UUID id, UUID imagingResultId);
}
