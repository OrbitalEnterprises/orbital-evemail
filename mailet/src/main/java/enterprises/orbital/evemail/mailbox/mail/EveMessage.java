package enterprises.orbital.evemail.mailbox.mail;

import enterprises.orbital.eve.esi.client.api.AllianceApi;
import enterprises.orbital.eve.esi.client.api.CharacterApi;
import enterprises.orbital.eve.esi.client.api.CorporationApi;
import enterprises.orbital.eve.esi.client.api.MailApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.model.*;
import enterprises.orbital.evemail.account.EveMailAccount;
import enterprises.orbital.evemail.mailbox.InMemoryMailboxManager;
import enterprises.orbital.evemail.mailbox.InMemoryMessageId;
import org.apache.james.mailbox.model.MessageAttachment;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.mail.model.Message;
import org.apache.james.mailbox.store.mail.model.Property;
import org.joda.time.DateTime;

import java.io.*;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Representation of a full EVE Online message.  Body is retrieved lazily if/when it is
 * needed.
 */
public class EveMessage implements Message {
  private static final Logger log = Logger.getLogger(EveMessage.class.getName());

  protected final int characterID;
  protected final long messageID;
  protected final DateTime timestamp;
  protected final String subject;
  protected boolean read;
  protected final EveRecipient from;
  protected final List<EveRecipient> recipients;
  protected String body = null;

  protected enum EveRecipientType {
    ALLIANCE,
    CHARACTER,
    CORPORATION,
    MAILING_LIST;

    public static EveRecipientType mapApiToType(GetCharactersCharacterIdMailRecipient.RecipientTypeEnum source) {
      switch (source) {
        case ALLIANCE:
          return ALLIANCE;
        case CHARACTER:
          return CHARACTER;
        case CORPORATION:
          return CORPORATION;
        case MAILING_LIST:
          return MAILING_LIST;
        default:
          throw new RuntimeException("Unexpected source type: " + source);
      }
    }
  }

  protected static class EveRecipient {
    public int recipientID;
    public EveRecipientType recipientType;
    public String recipientName;

    public EveRecipient(int recipientID, EveRecipientType recipientType, String recipientName) {
      this.recipientID = recipientID;
      this.recipientType = recipientType;
      this.recipientName = recipientName;
    }
  }

  public EveMessage(int characterID, GetCharactersCharacterIdMail200Ok header) {
    this.characterID = characterID;
    messageID = header.getMailId();
    timestamp = header.getTimestamp();
    subject = header.getSubject();
    read = header.getIsRead();
    recipients = new ArrayList<>();
    String senderName = null;
    try {
      senderName = getCharacterName(header.getFrom());
    } catch (ApiException e) {
      log.log(Level.WARNING, "Unable to resolve sending character ID: " + header.getFrom(), e);
    }
    from = new EveRecipient(header.getFrom(), EveRecipientType.CHARACTER, senderName == null ? "Unresolved" : senderName);
    String recipientName = null;
    for (GetCharactersCharacterIdMailRecipient i : header.getRecipients()) {
      recipientName = null;
      try {
        switch (i.getRecipientType()) {
          case ALLIANCE:
            recipientName = getAllianceName(i.getRecipientId());
            break;

          case CHARACTER:
            recipientName = getCharacterName(i.getRecipientId());
            break;

          case CORPORATION:
            recipientName = getCorporationName(i.getRecipientId());
            break;

          case MAILING_LIST:
            recipientName = getMailingList(characterID, i.getRecipientId());
            break;

          default:
            // Should never happen
            throw new RuntimeException("Unknown recipient type " + i.getRecipientType());
        }
      } catch (ApiException e) {
        log.log(Level.WARNING, "Unable to resolve recipient ID: " + i.getRecipientId(), e);
      }
      recipients.add(new EveRecipient(i.getRecipientId(), EveRecipientType.mapApiToType(i.getRecipientType()), recipientName == null ? "Unresolved" : recipientName));
    }
  }

  protected static String getCharacterName(int characterID) throws ApiException {
    CharacterApi apiInstance = new CharacterApi();
    List<Long> characterIds = Arrays.asList((long) characterID);
    String userAgent = InMemoryMailboxManager.getUserAgent();
    List<GetCharactersNames200Ok> result = apiInstance.getCharactersNames(characterIds, null, userAgent, null);
    return result.isEmpty() ? null : result.get(0).getCharacterName();
  }

  protected static String getCorporationName(int corporationID) throws ApiException {
    CorporationApi apiInstance = new CorporationApi();
    List<Long> corporationIds = Arrays.asList((long) corporationID);
    String userAgent = InMemoryMailboxManager.getUserAgent();
    List<GetCorporationsNames200Ok> result = apiInstance.getCorporationsNames(corporationIds, null, userAgent, null);
    return result.isEmpty() ? null : result.get(0).getCorporationName();
  }

  protected static String getAllianceName(int allianceID) throws ApiException {
    AllianceApi apiInstance = new AllianceApi();
    List<Long> allianceIds = Arrays.asList((long) allianceID);
    String userAgent = InMemoryMailboxManager.getUserAgent();
    List<GetAlliancesNames200Ok> result = apiInstance.getAlliancesNames(allianceIds, null, userAgent, null);
    return result.isEmpty() ? null : result.get(0).getAllianceName();
  }

  protected static String getMailingList(int characterID, int mailingListID) throws ApiException {
    try {
      MailApi apiInstance = new MailApi();
      String token = EveMailAccount.refresh(characterID, InMemoryMailboxManager.getTokenWindow());
      String userAgent = InMemoryMailboxManager.getUserAgent();
      List<GetCharactersCharacterIdMailLists200Ok> result = apiInstance.getCharactersCharacterIdMailLists(characterID, null, token, userAgent, null);
      for (GetCharactersCharacterIdMailLists200Ok i : result) {
        if (i.getMailingListId() == mailingListID) return i.getName();
      }
      return null;
    } catch (IOException e) {
      log.log(Level.SEVERE, "Error retrieving token for character ID: " + characterID, e);
      return null;
    }
  }

  protected String getBody() throws IOException {
    if (body != null) return body;
    synchronized (this) {
      if (body != null) return body;
      try {
        MailApi apiInstance = new MailApi();
        String token = EveMailAccount.refresh(characterID, InMemoryMailboxManager.getTokenWindow());
        String userAgent = InMemoryMailboxManager.getUserAgent();
        GetCharactersCharacterIdMailMailIdOk result = apiInstance.getCharactersCharacterIdMailMailId(characterID, (int) messageID, null, token, userAgent, null);
        body = result.getBody();
        return body;
      } catch (ApiException e) {
        throw new IOException(e);
      }
    }
  }

  // We store enough data to form the following standard header:
  //
  // From: John Doe <jdoe@machine.example>
  // To: Mary Smith <mary@example.net>
  // Subject: Saying Hello
  // Date: Fri, 21 Nov 1997 09:55:06 -0600
  // Message-ID: <message_id@evemail.orbital.enterprises>

  protected String getHeader() {
    StringBuilder builder = new StringBuilder();
    builder.append("From: ").append(from.recipientName).append(" <").append(from.recipientID).append("@char.evemail.orbital.enterprises>\r\n");
    builder.append("To: ");
    for (int i = 0; i < recipients.size(); i++) {
      EveRecipient r = recipients.get(i);
      builder.append(r.recipientName).append(" <").append(r.recipientID);
      switch (r.recipientType) {
        case CHARACTER:
          builder.append("@char.evemail.orbital.enterprises>");
        case CORPORATION:
          builder.append("@corp.evemail.orbital.enterprises>");
        case ALLIANCE:
          builder.append("@alliance.evemail.orbital.enterprises>");
        case MAILING_LIST:
          builder.append("@ml.evemail.orbital.enterprises>");
      }
      if (i < recipients.size() - 1) builder.append(", ");
    }
    builder.append("\r\n");
    builder.append("Subject: ").append(subject).append("\r\n");
    SimpleDateFormat format = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z");
    builder.append("Date: ").append(format.format(timestamp)).append("\r\n");
    builder.append("Message-ID: <").append(messageID).append("@evemail.orbital.enterprises>\r\n");
    return builder.toString();
  }

  @Override
  public MessageId getMessageId() {
    return InMemoryMessageId.of(messageID);
  }

  @Override
  public Date getInternalDate() {
    return timestamp.toDate();
  }

  @Override
  public InputStream getBodyContent() throws IOException {
    return new ByteArrayInputStream(getBody().getBytes(Charset.forName("UTF-8")));
  }

  @Override
  public String getMediaType() {
    return "text/plain";
  }

  @Override
  public String getSubType() {
    return null;
  }

  @Override
  public long getBodyOctets() {
    try {
      return getBody().length();
    } catch (IOException e) {
      return 0;
    }
  }

  @Override
  public long getFullContentOctets() {
    try {
      return (getHeader() + getBody()).length();
    } catch (IOException e) {
      return getHeader().length();
    }
  }

  @Override
  public Long getTextualLineCount() {
    String assembly = getHeader();
    try {
      assembly += getBody();
    } catch (IOException e) {
      // ignore
    }
    int count = 0;
    int i = assembly.indexOf("\r\n");
    while (i >= 0) {
      count++;
      i = assembly.indexOf("\r\n", i);
    }
    return (long) count;
  }

  @Override
  public InputStream getHeaderContent() throws IOException {
    return new ByteArrayInputStream(getHeader().getBytes(Charset.forName("UTF-8")));
  }

  @Override
  public InputStream getFullContent() throws IOException {
    String assembly = getHeader() + getBody();
    return new ByteArrayInputStream(assembly.getBytes(Charset.forName("UTF-8")));
  }

  @Override
  public List<Property> getProperties() {
    return Collections.emptyList();
  }

  @Override
  public List<MessageAttachment> getAttachments() {
    return Collections.emptyList();
  }
}
