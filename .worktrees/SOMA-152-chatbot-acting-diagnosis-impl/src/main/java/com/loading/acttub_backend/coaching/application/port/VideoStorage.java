package com.loading.acttub_backend.coaching.application.port;

import com.loading.acttub_backend.coaching.application.model.StoredVideo;
import com.loading.acttub_backend.coaching.application.model.VideoInput;

public interface VideoStorage {

	StoredVideo store(Long coachingId, VideoInput video);
}
