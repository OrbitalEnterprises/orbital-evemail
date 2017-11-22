package enterprises.orbital.evemail.mailet;

import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.base.GenericMatcher;

import javax.mail.MessagingException;
import java.util.ArrayList;
import java.util.Collection;

public class EveMailMatcher extends GenericMatcher {

  /**
   * Returns information about the matcher, such as author, version, and
   * copyright.  By default, this method returns an empty string. Override
   * this method to have it return a meaningful value.
   *
   * @return String information about this matcher, by default an empty string
   */
  public String getMatcherInfo() {
    return "EveMail EVE Address matcher, v1";
  }

  /**
   * <p>A convenience method which can be overridden so that there's no
   * need to call super.init(config).</p>
   *
   * <p>Instead of overriding init(MatcherConfig), simply override this
   * method and it will be called by GenericMatcher.init(MatcherConfig config).
   * The MatcherConfig object can still be retrieved via getMatcherConfig().</p>
   *
   * @throws MessagingException
   *          if an exception occurs that interrupts the matcher's normal operation
   */
  public void init() throws MessagingException {
    // TODO: setup swagger client?
  }

  /**
   * <p>Called by the matcher container to allow the matcher to process a
   * message.</p>
   *
   * <p>This method is declared abstract so subclasses must override it.</p>
   *
   * @param mail - the Mail object that contains the MimeMessage and
   *          routing information
   * @return java.util.Collection - the recipients that the mailet container should have the
   *          mailet affect.
   * @throws javax.mail.MessagingException - if an exception occurs that interferes with the mailet's normal operation
   *          occurred
   */
  public Collection<MailAddress> match(Mail mail) throws MessagingException
  {
    Collection<MailAddress> toprocess = new ArrayList<>();
    for (MailAddress next : mail.getRecipients()) {
      // Look for and resolve evemail domains.  Anything else is unprocessed.
      String domain = next.getDomain();
      switch (domain) {
        case "char.evemail.orbital.enterprises":
        case "corp.evemail.orbital.enterprises":
        case "alliance.evemail.orbital.enterprises":
//        case "ml.evemail.orbital.enterprises":
          // TODO: find out whether it is possible to map name to mailing list
          toprocess.add(next);
          break;

        default:
          // Unprocessed
      }
    }
    return toprocess;
  }
}

