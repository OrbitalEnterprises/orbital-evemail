package enterprises.orbital.evemail.mailbox.mail;

import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdMail200Ok;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.store.mail.model.FlagsBuilder;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;

import javax.mail.Flags;

public class EveMailboxMessage extends EveMessage implements MailboxMessage {

  private MessageUid uid;
  private final MailboxId mailboxId;
  private boolean flagged;
  private boolean seen;
  private String[] userFlags;
  private long modSeq;

  public EveMailboxMessage(int characterID, GetCharactersCharacterIdMail200Ok header, Flags flags, MailboxId mailboxId) {
    super(characterID, header);
    setFlags(flags);
    this.mailboxId = mailboxId;
    this.userFlags = flags.getUserFlags();
  }

  @Override
  public MailboxId getMailboxId() {
    return mailboxId;
  }

  @Override
  public MessageUid getUid() {
    return uid;
  }

  @Override
  public void setUid(MessageUid uid) {
    this.uid = uid;
  }

  @Override
  public void setModSeq(long modSeq) {
    this.modSeq = modSeq;
  }

  @Override
  public long getModSeq() {
    return modSeq;
  }

  @Override
  public boolean isAnswered() {
    // EVE doesn't expose this info
    return false;
  }

  @Override
  public boolean isDeleted() {
    // EVE doesn't expose this info
    return false;
  }

  @Override
  public boolean isDraft() {
    // EVE doesn't expose this info
    return false;
  }

  @Override
  public boolean isFlagged() {
    // TODO: figure out what this means in the context of EVE
    return flagged;
  }

  @Override
  public boolean isRecent() {
    // EVE doesn't expose this info
    return false;
  }

  @Override
  public boolean isSeen() {
    return read;
  }

  @Override
  public void setFlags(Flags flags) {
    // TODO: changing "seen" should set the "is read" flag in EVE
    flagged = flags.contains(Flags.Flag.FLAGGED);
    seen = flags.contains(Flags.Flag.SEEN);
    userFlags = flags.getUserFlags();
  }

  @Override
  public Flags createFlags() {
    return FlagsBuilder.createFlags(this, userFlags.clone());
  }

  @Override
  public int compareTo(MailboxMessage o) {
    return this.getUid().compareTo(o.getUid());
  }
}
