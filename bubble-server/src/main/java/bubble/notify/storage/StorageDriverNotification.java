/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.notify.storage;

import bubble.model.cloud.StorageMetadata;
import bubble.notify.SynchronousNotification;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import javax.persistence.Transient;

import static org.cobbzilla.util.daemon.ZillaRuntime.hashOf;

@NoArgsConstructor @Accessors(chain=true)
public class StorageDriverNotification extends SynchronousNotification {

    @Getter @Setter private String storageService;
    @Getter @Setter private String key;
    @Getter @Setter private StorageMetadata metadata;
    @Getter @Setter private String token;

    public StorageDriverNotification (String key, StorageMetadata metadata) {
        this.key = key;
        this.metadata = metadata;
    }

    public StorageDriverNotification (String key) { this(key, null); }

    @JsonIgnore @Transient @Getter(lazy=true) private final String cacheKey
            = hashOf(storageService, key, metadata == null ? null : metadata.getCacheKey());
}
