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

package enterprises.orbital.evemail.mailbox;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.base.PersistentProperty;
import enterprises.orbital.db.DBPropertyProvider;
import enterprises.orbital.evemail.account.EveMailAccountProvider;
import org.apache.james.mailbox.MailboxPathLocker;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.acl.GroupMembershipResolver;
import org.apache.james.mailbox.acl.MailboxACLResolver;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.*;
import org.apache.james.mailbox.store.event.DelegatingMailboxListener;
import org.apache.james.mailbox.store.event.MailboxEventDispatcher;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.mail.MessagingException;
import java.io.IOException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class InMemoryMailboxManager extends StoreMailboxManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(InMemoryMailboxManager.class);
    public static final String PROP_USER_AGENT = "enterprises.orbital.evemail.user_agent";
    public static final String DEF_USER_AGENT = "EveMail/1.0.0 (https://evemail.orbital.enterprises; deadlybulb@orbital.enterprises; )";
    public static final String PROP_TOKEN_WINDOW = "enterprises.orbital.evemail.tokenWindow";
    public static final long DEF_TOKEN_WINDOW = 60000L;

    protected static String userAgent;
    protected static long tokenWindow;

    @Override
    @PostConstruct
    public void init() throws MailboxException {
        super.init();
        userAgent = OrbitalProperties.getGlobalProperty(PROP_USER_AGENT, DEF_USER_AGENT);
        tokenWindow = OrbitalProperties.getLongGlobalProperty(PROP_TOKEN_WINDOW, DEF_TOKEN_WINDOW);
    }

    public static String getUserAgent() { return userAgent; }

    public static long getTokenWindow() { return tokenWindow; }

    @Inject
    public InMemoryMailboxManager(MailboxSessionMapperFactory mailboxSessionMapperFactory, Authenticator authenticator,
                                  Authorizator authorizator,
                                  MailboxPathLocker locker, MailboxACLResolver aclResolver,
                                  GroupMembershipResolver groupMembershipResolver,
                                  MessageParser messageParser, MessageId.Factory messageIdFactory,
                                  MailboxEventDispatcher dispatcher,
                                  DelegatingMailboxListener delegatingMailboxListener) {
        super(mailboxSessionMapperFactory, authenticator, authorizator, locker, aclResolver, groupMembershipResolver, messageParser, messageIdFactory,
              MailboxConstants.DEFAULT_LIMIT_ANNOTATIONS_ON_MAILBOX, MailboxConstants.DEFAULT_LIMIT_ANNOTATION_SIZE, dispatcher,
              delegatingMailboxListener);
    }

    public InMemoryMailboxManager(MailboxSessionMapperFactory mailboxSessionMapperFactory, Authenticator authenticator,
                                  Authorizator authorizator,
                                  MailboxPathLocker locker, MailboxACLResolver aclResolver,
                                  GroupMembershipResolver groupMembershipResolver,
                                  MessageParser messageParser, MessageId.Factory messageIdFactory) {
        super(mailboxSessionMapperFactory, authenticator, authorizator, locker, aclResolver, groupMembershipResolver, messageParser, messageIdFactory);
    }

    public InMemoryMailboxManager(MailboxSessionMapperFactory mailboxSessionMapperFactory, Authenticator authenticator,
                                  Authorizator authorizator,
                                  MailboxACLResolver aclResolver, GroupMembershipResolver groupMembershipResolver,
                                  MessageParser messageParser,
                                  MessageId.Factory messageIdFactory, int limitOfAnnotations, int limitAnnotationSize) {
        super(mailboxSessionMapperFactory, authenticator, authorizator, aclResolver, groupMembershipResolver, messageParser, messageIdFactory, limitOfAnnotations, limitAnnotationSize);
    }

    @Override
    public EnumSet<MailboxCapabilities> getSupportedMailboxCapabilities() {
        return EnumSet.of(MailboxCapabilities.Move, MailboxCapabilities.UserFlag, MailboxCapabilities.Namespace, MailboxCapabilities.Annotation);
    }

    @Override
    public EnumSet<MessageCapabilities> getSupportedMessageCapabilities() {
        return EnumSet.of(MessageCapabilities.Attachment, MessageCapabilities.UniqueID);
    }

    @Override
    protected StoreMessageManager createMessageManager(Mailbox mailbox,
                                                       MailboxSession session) throws MailboxException {
        return new InMemoryMessageManager(getMapperFactory(),
                                          getMessageSearchIndex(),
                                          getEventDispatcher(),
                                          getLocker(),
                                          mailbox,
                                          getAclResolver(),
                                          getGroupMembershipResolver(),
                                          getQuotaManager(),
                                          getQuotaRootResolver(),
                                          getMessageParser(),
                                          getMessageIdFactory(),
                                          getBatchSizes());
    }

    protected org.apache.james.mailbox.store.mail.model.Mailbox doCreateMailbox(MailboxPath mailboxPath,
                                                                                MailboxSession session) throws MailboxException {
        return new SimpleMailbox(mailboxPath, randomUidValidity());
    }

    public static int getCharacterID(MailboxSession.User user) throws MailboxException {
        String userName = user.getUserName();
        if (userName.indexOf('@') == -1) {
            String msg = "Account name in unexpected format: " + userName;
            LOGGER.error(msg);
            throw new MailboxException(msg);
        }
        userName = userName.substring(0, userName.indexOf('@'));
        try {
            return Integer.valueOf(userName);
        } catch (NumberFormatException e) {
            String msg = "Failed to parse character ID: " + userName;
            LOGGER.error(msg);
            throw new MailboxException(msg);
        }
    }
}