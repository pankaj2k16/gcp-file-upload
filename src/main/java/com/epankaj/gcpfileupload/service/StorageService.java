package com.epankaj.gcpfileupload.service;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID; // For generating unique file names

@Service
public class StorageService {

    // Autowire the Google Cloud Storage client
    @Autowired
    private Storage storage;

    // Inject the GCS bucket name from application.properties
    @Value("${gcs.bucket.name}")
    private String bucketName;

    // Inject the public URL prefix from application.properties
    // This value is used to construct publicly accessible URLs for files.
    @Value("${gcs.public.url-prefix}")
    private String gcsPublicUrlPrefix;

    /**
     * Uploads a file to Google Cloud Storage.
     * Generates a unique filename using UUID to avoid collisions.
     *
     * @param file The MultipartFile received from the client.
     * @return The unique filename generated for the uploaded file.
     * @throws IOException If an I/O error occurs during file processing.
     */
    public String uploadFile(MultipartFile file) throws IOException {
        // Generate a unique filename to prevent overwriting existing files
        String originalFilename = file.getOriginalFilename();
        String uniqueFilename = UUID.randomUUID().toString() + "_" + originalFilename;

        // Create BlobId for the new object in the bucket
        BlobId blobId = BlobId.of(bucketName, uniqueFilename);
        // Create BlobInfo with content type. Ensure content type is set for proper serving.
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                .setContentType(file.getContentType())
                .build();

        // Upload the file bytes to GCS
        // For very large files, consider using a resumable upload for robustness.
        storage.create(blobInfo, file.getBytes());

        return uniqueFilename;
    }

    /**
     * Lists all files in the configured Google Cloud Storage bucket.
     * Returns a list of public URLs for these files. This assumes the bucket
     * objects are configured for public readability (e.g., 'allUsers' with 'Storage Object Viewer' role).
     *
     * @return A list of public URLs of files in the bucket.
     */
    public List<String> listFiles() {
        List<String> fileUrls = new ArrayList<>();
        // Iterate over all blobs (files) in the specified bucket
        // The list operation provides an iterable of Blob objects.
        storage.list(bucketName).iterateAll()
                .forEach(blob -> {
                    // Construct the public URL for each blob using the configured prefix and blob name.
                    fileUrls.add(gcsPublicUrlPrefix + "/" + blob.getName());
                });
        return fileUrls;
    }

    /**
     * Downloads a specific file from Google Cloud Storage.
     *
     * @param filename The name of the file to download. This should be the unique filename generated during upload.
     * @return A ByteArrayResource containing the file's content, or null if the file is not found.
     */
    public ByteArrayResource downloadFile(String filename) {
        try {
            // Get the Blob (file object) from GCS using its bucket name and filename.
            Blob blob = storage.get(BlobId.of(bucketName, filename));

            // Check if the blob exists and is accessible.
            if (blob == null || !blob.exists()) {
                System.err.println("File not found in GCS: " + filename);
                return null;
            }

            // Read the file content into a byte array.
            byte[] content = blob.getContent();
            return new ByteArrayResource(content);
        } catch (Exception e) {
            // Log the error for debugging purposes.
            System.err.println("Error downloading file from GCS: " + filename + ". Error: " + e.getMessage());
            return null;
        }
    }
}