package enterprises.orbital.evemail.mailet;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.base.PersistentProperty;
import enterprises.orbital.db.DBPropertyProvider;
import enterprises.orbital.eve.esi.client.api.MailApi;
import enterprises.orbital.eve.esi.client.api.SearchApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.model.GetSearchOk;
import enterprises.orbital.eve.esi.client.model.PostCharactersCharacterIdMailMail;
import enterprises.orbital.eve.esi.client.model.PostCharactersCharacterIdMailRecipient;
import enterprises.orbital.evemail.account.EveMailAccount;
import enterprises.orbital.evemail.account.EveMailAccountProvider;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.base.GenericMailet;

import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class EveMailMailet extends GenericMailet {
  public static final String PROP_USER_AGENT = "enterprises.orbital.evemail.user_agent";
  public static final String DEF_USER_AGENT = "EveMail/1.0.0 (https://evemail.orbital.enterprises; deadlybulb@orbital.enterprises; )";
  public static final String PROP_TOKEN_WINDOW = "enterprises.orbital.evemail.tokenWindow";
  public static final long DEF_TOKEN_WINDOW = 60000L;

  enum MailTargetType {
    CHARACTER("character"),
    CORPORATION("corporation"),
    ALLIANCE("alliance");

    String value;

    MailTargetType(String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }
  }

  protected String userAgent;
  protected long tokenWindow;

  @Override
  public void init() throws MessagingException {
    try {
      OrbitalProperties.addPropertyFile("EveMailMailet.properties");
      PersistentProperty.setProvider(new DBPropertyProvider(OrbitalProperties.getGlobalProperty(EveMailAccountProvider.EVEMAIL_PU_PROP)));
      userAgent = OrbitalProperties.getGlobalProperty(PROP_USER_AGENT, DEF_USER_AGENT);
      tokenWindow = OrbitalProperties.getLongGlobalProperty(PROP_TOKEN_WINDOW, DEF_TOKEN_WINDOW);
    } catch (IOException e) {
      throw new MessagingException("Initialization error", e);
    }
  }

  // Parsing methods can handle one of three forms:
  // 1. entityID@entityType.evemail.orbital.enterprises
  // 2. "Entity Name"@entityType.evemail.orbital.enterprises
  // 3. Entity-Name@entityType.evemail.orbital.enterprises

  protected Object getNameOrID(String localPart) {
    // Checked for quoted local part
    if (localPart.startsWith("\"") && localPart.endsWith("\"")) {
      return localPart.substring(1, localPart.length() - 1);
    }
    // Try as an integer
    try {
      return Integer.valueOf(localPart);
    } catch (NumberFormatException e) {
      // If all else fails, assume dashed string.
      return localPart.replace('-', ' ');
    }
  }

  protected int mapToID(String localPart, MailTargetType tt) {
    Object addr = getNameOrID(localPart);
    if (addr instanceof Integer)
      // Already converted to character ID
      return (Integer) addr;
    // Otherwise, perform a search
    SearchApi api = new SearchApi();
    try {
      GetSearchOk result = api.getSearch(Arrays.asList(tt.getValue()), (String) addr, null, null, true, userAgent, null);
      List<Integer> targets = result.getCharacter();
      if (!targets.isEmpty())
        return targets.get(0);
    } catch (ApiException e) {
      log("Error resolving mail target: " + localPart, e);
    }
    return -1;
  }

  protected void addTarget(int id, PostCharactersCharacterIdMailRecipient.RecipientTypeEnum tt,
                           PostCharactersCharacterIdMailMail body) {
    PostCharactersCharacterIdMailRecipient tgt = new PostCharactersCharacterIdMailRecipient();
    tgt.setRecipientId(id);
    tgt.setRecipientType(tt);
    body.addRecipientsItem(tgt);
  }

  @Override
  public void service(Mail mail) throws MessagingException {
    // Resolve sender to EveMailAccount
    int senderCharacterID;
    String sender = (String) mail.getAttribute(Mail.SMTP_AUTH_USER_ATTRIBUTE_NAME);
    if (sender == null) {
      log("Message missing SMTP_AUTH_USER_ATTRIBUTE");
      getMailetContext().bounce(mail, "Authenticated sender missing");
      return;
    }
    if (sender.indexOf('@') == -1) {
      log("Message sender in unexpected format: " + sender);
      getMailetContext().bounce(mail, "Sender incorrect, should have form charid@char.evemail.orbital.enterprises");
      return;
    }
    sender = sender.substring(0, sender.indexOf('@'));
    try {
      senderCharacterID = Integer.valueOf(sender);
    } catch(NumberFormatException e) {
      log("Failed to parse sender ID: " + sender, e);
      getMailetContext().bounce(mail, "Sender incorrect, should have form charid@char.evemail.orbital.enterprises");
      return;
    }
    EveMailAccount senderAccount = EveMailAccount.getAccountByID(senderCharacterID);
    if (senderAccount == null) {
      log("Failed to find account for character ID: " + senderCharacterID);
      getMailetContext().bounce(mail, "Internal error resolving sender account, please try again.");
      return;
    }
    // Resolve each recipient to an appropriate ID and type
    Integer allianceTarget = null;
    Integer corpTarget = null;
    List<Integer> charTargets = new ArrayList<>();
    for (MailAddress next : mail.getRecipients()) {
      int nextID = -1;
      switch (next.getDomain()) {
        case "char.evemail.orbital.enterprises":
          nextID = mapToID(next.getLocalPart(), MailTargetType.CHARACTER);
          if (nextID != -1) charTargets.add(nextID);
          break;

        case "corp.evemail.orbital.enterprises":
          // TODO: verify corp target is senders corp
          if (corpTarget != null) {
            log("Only one corporation recipient is allowed");
            getMailetContext().bounce(mail, "Multiple corporation recipients specified, only one allowed.");
            return;
          }
          nextID = mapToID(next.getLocalPart(), MailTargetType.CORPORATION);
          if (nextID != -1) corpTarget = nextID;
          break;

        case "alliance.evemail.orbital.enterprises":
          // TODO: verify alliance target is sender's corp's alliance
          if (allianceTarget != null) {
            log("Only one alliance recipient is allowed");
            getMailetContext().bounce(mail, "Multiple alliance recipients specified, only one allowed.");
            return;
          }
          nextID = mapToID(next.getLocalPart(), MailTargetType.ALLIANCE);
          if (nextID != -1) allianceTarget = nextID;
          break;

        default:
      }
      if (nextID == -1) {
        log("Failed to map recipient, ignoring: " + next.asString());
      }
    }
    // Create and send message
    try {
      MimeMessage mm = mail.getMessage();
      MailApi apiInstance = new MailApi();
      // Set subject
      PostCharactersCharacterIdMailMail mailBody = new PostCharactersCharacterIdMailMail();
      mailBody.setSubject(mm.getSubject());
      // Append character targets
      for (int id : charTargets)
        addTarget(id, PostCharactersCharacterIdMailRecipient.RecipientTypeEnum.CHARACTER, mailBody);
      if (corpTarget != null)
        addTarget(corpTarget, PostCharactersCharacterIdMailRecipient.RecipientTypeEnum.CORPORATION, mailBody);
      if (allianceTarget != null)
        addTarget(allianceTarget, PostCharactersCharacterIdMailRecipient.RecipientTypeEnum.CORPORATION, mailBody);
      // Assemble body
      StringBuilder body = new StringBuilder();
      Object content = mm.getContent();
      if (content instanceof String) {
        // This represents text/plain, add this directly to the message
        body.append((String) content);
      } else if (content instanceof MimeMultipart) {
        // By convention, we append all the following MIME types:
        // text/plain
        // text/html
        MimeMultipart mimeContent = (MimeMultipart) content;
        for (int i = 0; i < mimeContent.getCount(); i++) {
          BodyPart nextPart = mimeContent.getBodyPart(i);
          if (nextPart.getContentType() == "text/plain" || nextPart.getContentType() == "text/html") {
            body.append(String.valueOf(nextPart.getContent()));
          }
        }
      }
      mailBody.setBody(body.toString());
      // Send message
      String token = EveMailAccount.refresh(senderCharacterID, tokenWindow);
      apiInstance.postCharactersCharacterIdMail(senderCharacterID, mailBody, null, token, userAgent, null);
      // Mark message as consumed
      mail.setState(Mail.GHOST);
    } catch (ApiException | IOException e) {
      log("API failure sending message from: " + senderCharacterID, e);
      getMailetContext().bounce(mail, "ESI error sending message, please try again");
    }
  }

  @Override
  public String getMailetInfo() {
    return "EveMail Mailet";
  }

}
