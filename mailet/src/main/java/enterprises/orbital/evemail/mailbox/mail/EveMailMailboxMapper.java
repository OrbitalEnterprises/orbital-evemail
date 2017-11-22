/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/
package enterprises.orbital.evemail.mailbox.mail;

import com.google.common.base.Objects;
import enterprises.orbital.eve.esi.client.api.MailApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdMailLabelsLabel;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdMailLabelsOk;
import enterprises.orbital.evemail.account.EveMailAccount;
import enterprises.orbital.evemail.mailbox.InMemoryId;
import enterprises.orbital.evemail.mailbox.InMemoryMailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxExistsException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailbox;
import org.apache.mailet.Mail;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Mailbox mapper for a given user.  We map the user to an EveMailAccount
 * to reflect the contents of their EVE Online mail to whatever protocol
 * (e.g. IMAP or POP) they happen to be using.  A "bounce" mailbox will be
 * created to hold errors when we can't reach EVE to access their mail.
 */
public class EveMailMailboxMapper implements MailboxMapper {
    protected static final Logger log = Logger.getLogger(EveMailMailboxMapper.class.getName());

    private static final int INITIAL_SIZE = 128;
    private final MailboxSession.User user;
    private final int characterID;
    private final ConcurrentHashMap<MailboxPath, Mailbox> mailboxesByPath;
    private final AtomicLong mailboxIdGenerator = new AtomicLong();

    // Inboxes which aren't allowed to be labels.  The following labels alias default EVE Online labels:
    //
    // Inbox, Sent, [Corp], [Alliance]
    //
    // The following labels are reserved by this service:
    //
    // Trash - always emptied as EVE Online doesn't expose the trash label
    // Bounced - captures bounced outgoing e-mail or authentication problems.  Purged periodically.
    //
    // Note that IMAP insists on naming Inbox in all caps (e.g. INBOX), so we reserve that name as well
    // and treat it as an alias for Inbox.

    public static final Set<String> reservedMailboxNames = Collections.unmodifiableSet(new HashSet<>(Arrays.asList("INBOX", "Inbox", "Trash", "Bounced", "Sent", "[Corp]", "[Alliance]")));

    public EveMailMailboxMapper(MailboxSession.User user) throws MailboxException {
        this.user = user;
        characterID = InMemoryMailboxManager.getCharacterID(user);
        mailboxesByPath = new ConcurrentHashMap<MailboxPath, Mailbox>(INITIAL_SIZE);

        // Populate initial set from EVE Online
        refreshMailboxes();

        // Every user has a "Trash" and "Bounced" mailbox
        for (String mbname : Arrays.asList("Trash", "Bounced")) {
            MailboxPath path = new MailboxPath(null, user.getUserName(), mbname);
            Mailbox box = new SimpleMailbox(path, mailboxIdGenerator.incrementAndGet());
            save(box);
        }

        // Remaining mailboxes are populated from user's EVE Online labels
        //        EveMailAccount senderAccount = EveMailAccount.getAccountByID(characterID);
        // TODO: these need to be updated periodically in case the user creates a new label in game
    }

    /**
     * Refresh list of mailboxes from EVE Online
     */
    protected void refreshMailboxes() throws MailboxException {
        EveMailAccount owner = EveMailAccount.getAccountByID(characterID);
        if (owner == null)
            throw new MailboxException("Unable to refresh mailboxes from EVE Online with current character ID");

        try {
            // Retrieve labels for owning character
            MailApi apiInstance = new MailApi();
            String token = EveMailAccount.refresh(characterID, InMemoryMailboxManager.getTokenWindow());
            GetCharactersCharacterIdMailLabelsOk result = apiInstance.getCharactersCharacterIdMailLabels(characterID, null, token, InMemoryMailboxManager.getUserAgent(), null);

            // Ensure all listed labels are populated as mailboxes
            Set<String> eveLabelNames = new HashSet<>();
            for (GetCharactersCharacterIdMailLabelsLabel label : result.getLabels()) {
                String name = label.getName();
                if (name.equals("Inbox")) name="INBOX";
                eveLabelNames.add(name);
                MailboxPath path = new MailboxPath(null, user.getUserName(), name);
                if (!mailboxesByPath.contains(path)) {
                    // Add missing mailbox
                    Mailbox box = new SimpleMailbox(path, mailboxIdGenerator.incrementAndGet());
                    save(box);
                }
            }

            // Remove mailboxes that are not labels and not reserved
            List<MailboxPath> toRemove = new ArrayList<>();
            for (MailboxPath existing : mailboxesByPath.keySet()) {
                String name = existing.getName();
                if (!eveLabelNames.contains(name) && !reservedMailboxNames.contains(name))
                    toRemove.add(existing);
            }
            for (MailboxPath remove : toRemove) mailboxesByPath.remove(remove);
        } catch (ApiException | IOException e) {
            log.log(Level.SEVERE, "Failed to refresh mailbox list", e);
            throw new MailboxException("Failed to refresh mailbox list", e);
        }

    }

    /**
     * @see MailboxMapper#delete(Mailbox)
     */
    public void delete(Mailbox mailbox) throws MailboxException {
        // TODO: if the mailbox represents an EVE label, then remove the appropriate label and refresh
        mailboxesByPath.remove(mailbox.generateAssociatedPath());
    }

    public void deleteAll() throws MailboxException {
        mailboxesByPath.clear();
    }

    /**
     * @see MailboxMapper#findMailboxByPath(MailboxPath)
     */
    public synchronized Mailbox findMailboxByPath(MailboxPath path) throws MailboxException {
        Mailbox result = mailboxesByPath.get(path);
        if (result == null) {
            throw new MailboxNotFoundException(path);
        } else {
            return new SimpleMailbox(result);
        }
    }

    public synchronized Mailbox findMailboxById(MailboxId id) throws MailboxException {
        InMemoryId mailboxId = (InMemoryId)id;
        for (Mailbox mailbox: mailboxesByPath.values()) {
            if (mailbox.getMailboxId().equals(mailboxId)) {
                return new SimpleMailbox(mailbox);
            }
        }
        throw new MailboxNotFoundException(mailboxId.serialize());
    }

    /**
     * @see MailboxMapper#findMailboxWithPathLike(MailboxPath)
     */
    public List<Mailbox> findMailboxWithPathLike(MailboxPath path) throws MailboxException {
        final String regex = path.getName().replace("%", ".*");
        log.log(Level.INFO, "Searching for mailboxes which match " + regex);
        List<Mailbox> results = new ArrayList<Mailbox>();
        for (Mailbox mailbox: mailboxesByPath.values()) {
            if (mailboxMatchesRegex(mailbox, path, regex)) {
                log.log(Level.INFO, "Found: " + mailbox.getName());
                results.add(new SimpleMailbox(mailbox));
            }
        }
        return results;
    }

    private boolean mailboxMatchesRegex(Mailbox mailbox, MailboxPath path, String regex) {
        return Objects.equal(mailbox.getNamespace(), path.getNamespace())
            && Objects.equal(mailbox.getUser(), path.getUser())
            && mailbox.getName().matches(regex);
    }

    /**
     * @see MailboxMapper#save(Mailbox)
     */
    public MailboxId save(Mailbox mailbox) throws MailboxException {
        InMemoryId id = (InMemoryId) mailbox.getMailboxId();
        if (id == null) {
            id = InMemoryId.of(mailboxIdGenerator.incrementAndGet());
            ((SimpleMailbox) mailbox).setMailboxId(id);
        } else {
            try {
                Mailbox mailboxWithPreviousName = findMailboxById(id);
                mailboxesByPath.remove(mailboxWithPreviousName.generateAssociatedPath());
            } catch (MailboxNotFoundException e) {
                // No need to remove the previous enterprises.orbital.evemail.mailbox
            }
        }
        Mailbox previousMailbox = mailboxesByPath.putIfAbsent(mailbox.generateAssociatedPath(), mailbox);
        if (previousMailbox != null) {
            throw new MailboxExistsException(mailbox.getName());
        }
        log.log(Level.INFO, "Saving mailbox: " + mailbox.getMailboxId().serialize());
        return mailbox.getMailboxId();
    }

    /**
     * Do nothing
     */
    public void endRequest() {
        // Do nothing
    }

    /**
     * @see MailboxMapper#hasChildren(Mailbox, char)
     */
    public boolean hasChildren(Mailbox mailbox, char delimiter) throws MailboxException {
        String mailboxName = mailbox.getName() + delimiter;
        for (Mailbox box: mailboxesByPath.values()) {
            if (belongsToSameUser(mailbox, box) && box.getName().startsWith(mailboxName)) {
                return true;
            }
        }
        return false;
    }

    private boolean belongsToSameUser(Mailbox mailbox, Mailbox otherMailbox) {
        return Objects.equal(mailbox.getNamespace(), otherMailbox.getNamespace())
            && Objects.equal(mailbox.getUser(), otherMailbox.getUser());
    }

    /**
     * @see MailboxMapper#list()
     */
    public List<Mailbox> list() throws MailboxException {
        refreshMailboxes();
        return new ArrayList<Mailbox>(mailboxesByPath.values());
    }

    public <T> T execute(Transaction<T> transaction) throws MailboxException {
        return transaction.run();
    }

    @Override
    public void updateACL(Mailbox mailbox, MailboxACL.MailboxACLCommand mailboxACLCommand) throws MailboxException{
        mailbox.setACL(mailbox.getACL().apply(mailboxACLCommand));
    }
}
