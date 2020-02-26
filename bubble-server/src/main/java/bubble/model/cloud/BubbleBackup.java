/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.model.cloud;

import bubble.model.account.Account;
import bubble.model.account.HasAccount;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.wizard.model.IdentifiableBase;
import org.cobbzilla.wizard.model.entityconfig.EntityFieldType;
import org.cobbzilla.wizard.model.entityconfig.annotations.*;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import static bubble.ApiConstants.BACKUPS_ENDPOINT;
import static bubble.ApiConstants.ERROR_MAXLEN;
import static bubble.service.backup.BackupService.BR_STATE_LOCK_TIMEOUT;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.string.StringUtil.ellipsis;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENCRYPTED_STRING;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENC_PAD;

@ECType(root=true, name="backup") @ECTypeCreate(method="DISABLED")
@ECTypeURIs(baseURI=BACKUPS_ENDPOINT, listFields={"network", "label", "path"})
@Entity @NoArgsConstructor @Accessors(chain=true)
@ECIndexes({ @ECIndex(unique=true, of={"network", "path"}) })
public class BubbleBackup extends IdentifiableBase implements HasAccount {

    @Override public void beforeCreate() {
        if (getUuid() == null) initUuid();
    }

    @ECSearchable @ECField(index=10)
    @ECForeignKey(entity=Account.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String account;

    @ECSearchable @ECField(index=20)
    @ECForeignKey(entity=BubbleNetwork.class)
    @Column(nullable=false, updatable=false, length=UUID_MAXLEN)
    @Getter @Setter private String network;

    @ECSearchable(filter=true) @ECField(index=30, type=EntityFieldType.opaque_string)
    @Size(max=2000, message="err.path.length")
    @Type(type=ENCRYPTED_STRING) @Column(updatable=false, columnDefinition="varchar("+(2000+ENC_PAD)+") NOT NULL")
    @Getter @Setter private String path;

    @ECSearchable(filter=true) @ECField(index=40)
    @Pattern(regexp="[A-Za-z0-9][-A-Za-z0-9\\._]{2,}", message="err.label.invalid")
    @Size(max=300, message="err.label.length")
    @Type(type=ENCRYPTED_STRING) @Column(updatable=false, columnDefinition="varchar("+(300+ENC_PAD)+")")
    @Getter @Setter private String label;
    public boolean hasLabel () { return !empty(label); }

    @Override @JsonIgnore @Transient public String getName() { return hasLabel() ? getLabel() : getPath(); }

    @ECSearchable @ECField(index=50)
    @Enumerated(EnumType.STRING)
    @ECIndex @Column(nullable=false, length=40)
    @Getter @Setter private BackupStatus status;
    public boolean success () { return status == BackupStatus.backup_completed; }

    @ECSearchable(filter=true) @ECField(index=60)
    @Type(type=ENCRYPTED_STRING) @Column(updatable=false, columnDefinition="varchar("+(ERROR_MAXLEN+ENC_PAD)+")")
    @Getter private String error;
    public BubbleBackup setError (String err) { this.error = ellipsis(err, ERROR_MAXLEN); return this; }
    public boolean hasError () { return !empty(error); }

    public boolean canDelete() { return status.isDeletable() || getCtimeAge() > BR_STATE_LOCK_TIMEOUT; }
}
