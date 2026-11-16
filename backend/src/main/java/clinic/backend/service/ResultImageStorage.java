package clinic.backend.service;

/** UC-67: where the images of an imaging or functional result are kept (Cloudinary in the SRS). */
public interface ResultImageStorage {

    boolean isConfigured();

    /** The folder new images go to; part of their public id. */
    String folder();

    /** Stores the image under {@code publicId} and returns its HTTPS address. */
    String upload(String publicId, byte[] content, String contentType);

    /** Removes the image; a missing image is not an error. */
    void delete(String publicId);
}
