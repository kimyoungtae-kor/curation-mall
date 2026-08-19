package kr.co.petcuration.admin.api;

import kr.co.petcuration.admin.api.AdminApiModels.Envelope;
import kr.co.petcuration.admin.api.AdminApiModels.MediaUpload;
import kr.co.petcuration.admin.application.AdminImageUploadService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/media")
public class AdminMediaController {

    private final AdminImageUploadService imageUploadService;

    public AdminMediaController(AdminImageUploadService imageUploadService) {
        this.imageUploadService = imageUploadService;
    }

    @PostMapping(path = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<Envelope<MediaUpload>> uploadImage(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(new Envelope<>(imageUploadService.upload(file)));
    }
}
