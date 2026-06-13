package com.loading.acttub_backend.coaching.application.port;

import com.loading.acttub_backend.coaching.application.model.StoredVideo;
import org.springframework.web.multipart.MultipartFile;

public interface VideoStorage {

	StoredVideo store(Long coachingId, MultipartFile video);
}
