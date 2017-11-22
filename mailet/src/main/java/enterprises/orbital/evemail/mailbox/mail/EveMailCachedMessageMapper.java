package enterprises.orbital.evemail.mailbox.mail;

import enterprises.orbital.evemail.mailbox.InMemoryId;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.*;
import org.apache.james.mailbox.store.SimpleMessageMetaData;
import org.apache.james.mailbox.store.mail.AbstractMessageMapper;
import org.apache.james.mailbox.store.mail.ModSeqProvider;
import org.apache.james.mailbox.store.mail.UidProvider;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.Property;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.apache.james.mailbox.store.mail.utils.ApplicableFlagCalculator;

import javax.mail.Flags;
import javax.mail.Flags.Flag;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Message mapper backed by access to the mail account of an EVE Online character.
 */
public class EveMailCachedMessageMapper extends AbstractMessageMapper {
    private static final int INITIAL_SIZE = 256;

    private final Map<InMemoryId, EveMailboxID> idToBox;
    private final Map<InMemoryId, Map<MessageUid, MailboxMessage>> mailboxByUid;
    private final MailboxSession.User user;

    private class EveMailboxID {
        public int labelID;
        public String labelName;

        public EveMailboxID(int labelID) {
            this.labelID = labelID;
        }
    }


    /**
     * Populate all messages from EVE Online for the given mailbox id.
     * This call is ignored if the mailbox represents "Trash" or "Bounced" since these mailboxes
     * are not reflected in EVE.
     *
     * @param id the mailbox to populate.
     * @throws MailboxException
     */
    private void refreshMailbox(InMemoryId id) throws MailboxException {
        // Determine the mailbox label for the target mailbox
        EveMailboxID eveID = idToBox.get(id);
        if (eveID == null) throw new MailboxException("Unknown mailbox: " + id);
        if (eveID.labelName.equals("Trash") || eveID.labelName.equals("Bounced")) return;

        // Retrieve message headers associated with the given label

        // Verify a message exists for each retrieved header.  Add any that are missing.

        // Remove any messages for which a header no longer exists.


//        message.setUid(uidProvider.nextUid(mailboxSession, mailbox));
//
//        // if a mailbox does not support mod-sequences the provider may be null
//        if (modSeqProvider != null) {
//            message.setModSeq(modSeqProvider.nextModSeq(mailboxSession, mailbox));
//        }
//        MessageMetaData data = save(mailbox, message);
//
//        return data;

//        SimpleMailboxMessage copy = SimpleMailboxMessage.copy(mailbox.getMailboxId(), message);
//        copy.setUid(message.getUid());
//        copy.setModSeq(message.getModSeq());
//        getMembershipByUidForMailbox(mailbox).put(message.getUid(), copy);
//
//        return new SimpleMessageMetaData(message);



    }

    public EveMailCachedMessageMapper(MailboxSession session, UidProvider uidProvider, ModSeqProvider modSeqProvider) {
        super(session, uidProvider, modSeqProvider);
        this.idToBox = new ConcurrentHashMap<>(INITIAL_SIZE);
        this.mailboxByUid = new ConcurrentHashMap<>(INITIAL_SIZE);
        this.user = session.getUser();
    }

    private Map<MessageUid, MailboxMessage> getMembershipByUidForMailbox(Mailbox mailbox) {
        return getMembershipByUidForId((InMemoryId) mailbox.getMailboxId());
    }

    private Map<MessageUid, MailboxMessage> getMembershipByUidForId(InMemoryId id) {
        Map<MessageUid, MailboxMessage> membershipByUid = mailboxByUid.get(id);
        if (membershipByUid == null) {
            membershipByUid = new ConcurrentHashMap<MessageUid, MailboxMessage>(INITIAL_SIZE);
            mailboxByUid.put(id, membershipByUid);
        }
        return membershipByUid;
    }

    @Override
    public long countMessagesInMailbox(Mailbox mailbox) throws MailboxException {
        return getMembershipByUidForMailbox(mailbox).size();
    }

    @Override
    public long countUnseenMessagesInMailbox(Mailbox mailbox) throws MailboxException {
        long count = 0;
        for (MailboxMessage member : getMembershipByUidForMailbox(mailbox).values()) {
            if (!member.isSeen()) {
                count++;
            }
        }
        return count;
    }

    @Override
    public void delete(Mailbox mailbox, MailboxMessage message) throws MailboxException {
        getMembershipByUidForMailbox(mailbox).remove(message.getUid());
    }

    @Override
    public MessageMetaData move(Mailbox mailbox, MailboxMessage original) throws MailboxException {
        InMemoryId originalMailboxId = (InMemoryId) original.getMailboxId();
        MessageUid uid = original.getUid();
        MessageMetaData messageMetaData = copy(mailbox, original);
        getMembershipByUidForId(originalMailboxId).remove(uid);
        return messageMetaData;
    }

    @Override
    public Iterator<MailboxMessage> findInMailbox(Mailbox mailbox, MessageRange set, FetchType ftype, int max)
            throws MailboxException {
        List<MailboxMessage> results = new ArrayList<MailboxMessage>(getMembershipByUidForMailbox(mailbox).values());
        for (Iterator<MailboxMessage> it = results.iterator(); it.hasNext();) {
            if (!set.includes(it.next().getUid())) {
                it.remove();
            }
        }
        
        Collections.sort(results);

        if (max > 0 && results.size() > max) {
            results = results.subList(0, max);
        }
        return results.iterator();
    }

    @Override
    public List<MessageUid> findRecentMessageUidsInMailbox(Mailbox mailbox) throws MailboxException {
        final List<MessageUid> results = new ArrayList<MessageUid>();
        for (MailboxMessage member : getMembershipByUidForMailbox(mailbox).values()) {
            if (member.isRecent()) {
                results.add(member.getUid());
            }
        }
        Collections.sort(results);

        return results;
    }

    @Override
    public MessageUid findFirstUnseenMessageUid(Mailbox mailbox) throws MailboxException {
        List<MailboxMessage> memberships = new ArrayList<MailboxMessage>(getMembershipByUidForMailbox(mailbox).values());
        Collections.sort(memberships);
        for (MailboxMessage m : memberships) {
            if (m.isSeen() == false) {
                return m.getUid();
            }
        }
        return null;
    }

    @Override
    public Map<MessageUid, MessageMetaData> expungeMarkedForDeletionInMailbox(Mailbox mailbox, MessageRange set)
            throws MailboxException {
        final Map<MessageUid, MessageMetaData> filteredResult = new HashMap<MessageUid, MessageMetaData>();

        Iterator<MailboxMessage> it = findInMailbox(mailbox, set, FetchType.Metadata, -1);
        while (it.hasNext()) {
            MailboxMessage member = it.next();
            if (member.isDeleted()) {
                filteredResult.put(member.getUid(), new SimpleMessageMetaData(member));

                delete(mailbox, member);
            }
        }
        return filteredResult;
    }

    @Override
    public Flags getApplicableFlag(Mailbox mailbox) throws MailboxException {
        return new ApplicableFlagCalculator(getMembershipByUidForId((InMemoryId) mailbox.getMailboxId()).values())
            .computeApplicableFlags();
    }

    public void deleteAll() {
        mailboxByUid.clear();
    }

    @Override
    public void endRequest() {
        // Do nothing
    }

    @Override
    protected MessageMetaData copy(Mailbox mailbox, MessageUid uid, long modSeq, MailboxMessage original)
            throws MailboxException {
        SimpleMailboxMessage message = SimpleMailboxMessage.copy(mailbox.getMailboxId(), original);
        message.setUid(uid);
        message.setModSeq(modSeq);
        Flags flags = original.createFlags();

        // Mark message as recent as it is a copy
        flags.add(Flag.RECENT);
        message.setFlags(flags);
        return save(mailbox, message);
    }

    @Override
    protected MessageMetaData save(Mailbox mailbox, MailboxMessage message) throws MailboxException {
        SimpleMailboxMessage copy = SimpleMailboxMessage.copy(mailbox.getMailboxId(), message);
        copy.setUid(message.getUid());
        copy.setModSeq(message.getModSeq());
        getMembershipByUidForMailbox(mailbox).put(message.getUid(), copy);

        return new SimpleMessageMetaData(message);
    }

    @Override
    protected void begin() throws MailboxException {

    }

    @Override
    protected void commit() throws MailboxException {

    }

    @Override
    protected void rollback() throws MailboxException {
    }
}
