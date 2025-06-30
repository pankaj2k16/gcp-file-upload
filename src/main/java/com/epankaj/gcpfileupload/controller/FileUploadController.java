package com.epankaj.gcpfileupload.controller;

import com.epankaj.gcpfileupload.service.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource; // For download method
import org.springframework.http.HttpHeaders; // For download method
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType; // For download method
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api/files")
public class FileUploadController {

    // logger using SLF4J
    private static final Logger logger = LoggerFactory.getLogger(FileUploadController.class);

    @Autowired
    private StorageService storageService;

    // Hello API for basic reachability check
    @GetMapping("/hello")
    public ResponseEntity<String> hello() {
        logger.info("Hello API called");
        return ResponseEntity.ok("Hello, GCP File Upload!");
    }

    /**
     * Handles file uploads to Google Cloud Storage.
     * Expects a MultipartFile named "file" in the request.
     *
     * @param file The MultipartFile containing the file to be uploaded.
     * @return ResponseEntity with a success or error message.
     */
    @PostMapping("/upload")
    public ResponseEntity<ResponseMessage> uploadFile(@RequestParam("file") MultipartFile file) {
        logger.info("File upload request received for file: {}", file.getOriginalFilename());
        String message = "";
        try {
            // Call the StorageService to handle the file upload to GCS.
            String uploadedFilename = storageService.uploadFile(file);
            message = "Uploaded the file successfully: " + uploadedFilename;
            logger.info(message);
            return ResponseEntity.status(HttpStatus.OK).body(new ResponseMessage(message));
        } catch (Exception e) {
            message = "Could not upload the file: " + file.getOriginalFilename() + "! Error: " + e.getMessage();
            logger.error(message, e);
            return ResponseEntity.status(HttpStatus.EXPECTATION_FAILED).body(new ResponseMessage(message));
        }
    }

    /**
     * Lists all uploaded files (returns their public URLs).
     * This endpoint retrieves all object names from the configured GCS bucket
     * and constructs their public URLs.
     *
     * @return ResponseEntity with a list of file URLs or an error message.
     */
    @GetMapping
    public ResponseEntity<List<String>> getListFiles() {
        logger.info("Request received to list all files.");
        try {
            // Call the StorageService to get a list of public URLs for all files.
            List<String> fileUrls = storageService.listFiles();
            logger.info("Successfully retrieved {} file URLs.", fileUrls.size());
            return ResponseEntity.status(HttpStatus.OK).body(fileUrls);
        } catch (Exception e) {
            String errorMessage = "Could not list files. Error: " + e.getMessage();
            logger.error(errorMessage, e);
            // Return a structured error response, perhaps with an empty list or specific error object.
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(List.of(errorMessage)); // Returning a list containing the error message for consistency
        }
    }

    /**
     * Downloads a specific file by its unique filename.
     *
     * @param filename The unique name of the file to download from GCS.
     * @return ResponseEntity with the file content as a ByteArrayResource, or an error/not found status.
     */
    @GetMapping("/{filename}")
    public ResponseEntity<ByteArrayResource> downloadFile(@PathVariable String filename) {
        logger.info("Request received to download file: {}", filename);
        ByteArrayResource resource = storageService.downloadFile(filename);

        if (resource == null) {
            logger.warn("File not found for download: {}", filename);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }

        // Determine content type. In a more robust system, this might be stored with the file metadata.
        String contentType = "application/octet-stream"; // Default generic binary file type
        if (filename.toLowerCase().endsWith(".png")) {
            contentType = "image/png";
        } else if (filename.toLowerCase().endsWith(".jpg") || filename.toLowerCase().endsWith(".jpeg")) {
            contentType = "image/jpeg";
        } else if (filename.toLowerCase().endsWith(".pdf")) {
            contentType = "application/pdf";
        } else if (filename.toLowerCase().endsWith(".txt")) {
            contentType = "text/plain";
        }
        // Add more content types as needed for common file types.

        logger.info("Successfully prepared file {} for download with content type: {}", filename, contentType);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(resource);
    }
}