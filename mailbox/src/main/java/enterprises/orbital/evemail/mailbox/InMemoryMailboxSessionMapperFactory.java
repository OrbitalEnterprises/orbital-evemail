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

import enterprises.orbital.evemail.mailbox.mail.InMemoryAttachmentMapper;
import enterprises.orbital.evemail.mailbox.mail.InMemoryMailboxMapper;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.SubscriptionException;
import enterprises.orbital.evemail.mailbox.mail.InMemoryAnnotationMapper;
import enterprises.orbital.evemail.mailbox.mail.InMemoryMessageMapper;
import enterprises.orbital.evemail.mailbox.mail.InMemoryModSeqProvider;
import enterprises.orbital.evemail.mailbox.mail.InMemoryUidProvider;
import enterprises.orbital.evemail.mailbox.user.InMemorySubscriptionMapper;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.mail.AnnotationMapper;
import org.apache.james.mailbox.store.mail.AttachmentMapper;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.MessageIdMapper;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.ModSeqProvider;
import org.apache.james.mailbox.store.mail.UidProvider;
import org.apache.james.mailbox.store.user.SubscriptionMapper;

public class InMemoryMailboxSessionMapperFactory extends MailboxSessionMapperFactory {

    private final MailboxMapper mailboxMapper;
    private final MessageMapper messageMapper;
    private final SubscriptionMapper subscriptionMapper;
    private final AttachmentMapper attachmentMapper;
    private final AnnotationMapper annotationMapper;
    private final InMemoryUidProvider uidProvider;
    private final InMemoryModSeqProvider modSeqProvider;

    public InMemoryMailboxSessionMapperFactory() {
        mailboxMapper = new InMemoryMailboxMapper();
        uidProvider = new InMemoryUidProvider();
        modSeqProvider = new InMemoryModSeqProvider();
        messageMapper = new InMemoryMessageMapper(null, uidProvider, modSeqProvider);
        subscriptionMapper = new InMemorySubscriptionMapper();
        attachmentMapper = new InMemoryAttachmentMapper();
        annotationMapper = new InMemoryAnnotationMapper();
    }
    
    @Override
    public MailboxMapper createMailboxMapper(MailboxSession session) throws MailboxException {
        return mailboxMapper;
    }

    @Override
    public MessageMapper createMessageMapper(MailboxSession session) throws MailboxException {
        return messageMapper;
    }

    @Override
    public MessageIdMapper createMessageIdMapper(MailboxSession session) {
        return null;
    }

    @Override
    public SubscriptionMapper createSubscriptionMapper(MailboxSession session) throws SubscriptionException {
        return subscriptionMapper;
    }
    
    @Override
    public AttachmentMapper createAttachmentMapper(MailboxSession session) throws MailboxException {
        return attachmentMapper;
    }

    public void deleteAll() throws MailboxException {
        ((InMemoryMailboxMapper) mailboxMapper).deleteAll();
        ((InMemoryMessageMapper) messageMapper).deleteAll();
        ((InMemorySubscriptionMapper) subscriptionMapper).deleteAll();
    }

    @Override
    public AnnotationMapper createAnnotationMapper(MailboxSession session)
            throws MailboxException {
        return annotationMapper;
    }

    @Override
    public UidProvider getUidProvider() {
        return uidProvider;
    }

    @Override
    public ModSeqProvider getModSeqProvider() {
        return modSeqProvider;
    }

}