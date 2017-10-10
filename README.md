# Orbital EVE Mail Server (DRAFT)

The Orbital EVE Mail Server is a standard e-mail server which allows access to EVE Online in-game e-mail via internet e-mail standards (e.g. SMTP - RFC 5321, IMAP - RFC 3501, POP3 - RFC 1939).  Users access their EVE Online e-mail by configuring a standard internet e-mail client to connect to the Orbital EVE Mail Server, much in the same way one would configure an e-mail client to access any standard IMAP or POP3 service.  The inclusion of support for SMTP allows clients to also send e-mail which is delivered in-game to EVE Online e-mail recipients.

## Setup

Using the mail server requires two setup steps:

1. Create an e-mail account on the Orbital EVE Mail Server; and,
2. Configure an internet e-mail client to connect to the server.

An account on the Orbital server is created as follows:

1. Create a login on the server by authenticating using any valid EVE credentials (via EVE SSO).  This initial login will serve as the management account for all e-mail accounts you subsequently create.  Note that you will not be asked to authorize any scopes for the initial login.
2. For each EVE account for which e-mail will be accessed, perform the following steps:
	1. Determine whether you will use IMAP or POP3 to access this account.  This selection can be changed later as needed.
	2. Click the "create account" button and authenticate using the EVE credentials for this account.  You will be asked to authorize the EVE mail access scopes.
	3. Create a unique e-mail password for this account.  You will need this password when configuring your internet e-mail client.  You can change or reset this password later.
	4. Accept the new account.  Note the username automatically created for the account, as well as the password you specified.

Once one or more e-mail accounts have been created, you can configure your internet e-mail client as follows:

1. For all clients, the username and e-mail address you specify in your e-mail client are irrelevant.  The Orbital server will always replace these values with your EVE character name and an e-mail address of the form &lt;char_1234@evemail&gt; where "1234" is the unique character ID for your character.
2. For IMAP clients:
	1. Copy the username and password from the account creation step above.
	2. Set the incoming server to `evemail.orbital.enterprises` and use the default IMAP port (143).  You may use SSL to connect if you wish (port 993).
	3. Set the outgoing server to `evemail.orbital.enterprises` and use the default SMTP port (25).  You may use SSL to connect if you wish (port 465).
3. For POP clients:
	1. Follow the same steps as for IMAP clients, except use the default POP3 port (110).  You may use SSL to connect if you wish (port 995).
	2. Many POP3 clients will offer to delete e-mail from the server after it has been retrieved.  We recommend that you DISABLE this feature, as leaving this enabled will remove your EVE mail from in-game (with no way to put it back).

## Mapping EVE Mail to Internet E-mail

EVE mail is represented in two parts: a mail header, and a mail body.  A typical EVE mail header appears as follows:

```json
{
  "application/json": [
    {
      "from": 1234,
      "is_read": true,
      "labels": [
        2,
        32
      ],
      "mail_id": 7,
      "recipients": [
        {
          "recipient_id": 1111,
          "recipient_type": "character"
        },
        {
        	"recipient_id": 2222,
        	"recipient_type": "corporation"
        },
        {
        	"recipient_id": 3333,
        	"recipient_type": "alliance"
        },
        {
        	"recipient_id": 4444,
        	"recipient_type": "mailing_list"
        }
      ],
      "subject": "message subject",
      "timestamp": "2015-09-30T16:07:00Z"
    }
  ]
}
```
A corresponding EVE mail body might appear as follows:

```json
{
  "application/json": {
    "body": "blah blah blah",
    "from": 1234,
    "labels": [
      2,
      32
    ],
    "read": true,
    "subject": "message subject",
    "timestamp": "2015-09-30T16:07:00Z"
  }
}
```

This representation can be mapped to a form compatible with internet mail standards as shown in the following table:

<table>
<tr>
<th nowrap>EVE Mail Attribute</th>
<th nowrap>RFC 5322 (Inet Mail)</th>
<th nowrap>RFC 3501 (IMAP)</th>
<th nowrap>RFC 1939 (POP3)</th>
</tr>
<tr>
<td>
<pre lang="json">
"from" : 1234
</pre>
</td>
<td nowrap>
From: Char Name &lt;char_1234@evemail&gt;
</td>
<td>N/A</td>
<td>N/A</td>
</tr>
<tr>
<td>
<pre lang="json">
"recipients": [
  {
    "recipient_id": 1111,
    "recipient_type": "character"
  },
  {
    "recipient_id": 2222,
    "recipient_type": "corporation"
  },
  {
    "recipient_id": 3333,
    "recipient_type": "alliance"
  },
  {
  	"recipient_id": 4444,
  	"recipient_type": "mailing_list"
  }
]
</pre>
</td>
<td>
To: Char Name &lt;1111@char.evemail&gt;, Corp Name &lt;2222@corp.evemail&gt;, Alliance Name &lt;3333@alliance.evemail&gt;, Mailing List &lt;4444@ml.evemail&gt;
</td>
<td>N/A</td>
<td>N/A</td>
</tr>
<tr>
<td>
<pre lang="json">
"subject": "message subject"
</pre>
</td>
<td>
Subject: message subject
</td>
<td>N/A</td>
<td>N/A</td>
</tr>
<tr>
<td>
<pre lang="json">
"timestamp": "2015-09-30T16:07:00Z"
</pre>
</td>
<td nowrap>
Date: Wed, 30 Sep 2015 16:07:00 +0000
</td>
<td>N/A</td>
<td>N/A</td>
</tr>
<tr>
<td>
<pre lang="json">
"mail_id": 7
</pre>
</td>
<td>
Message-ID: &lt;7_1111@evemail&gt;
</td>
<td>N/A</td>
<td>N/A</td>
</tr>
<tr>
<td>
<pre lang="json">
"body": "blah blah blah"
</pre>
</td>
<td>
Content-Type: text/plain; charset="UTF-8"<br><br>
blah blah blah
</td>
<td>N/A</td>
<td>N/A</td>
</tr>
<tr>
<td>
<pre lang="json">
"labels": [
  2,
  32
]
</pre>
</td>
<td>N/A</td>
<td>Folder per label</td>
<td>Keywords: label1, label2</td>
</tr>
<tr>
<td>
<pre lang="json">
"read": true
</pre>
</td>
<td>N/A</td>
<td>\Seen message attribute</td>
<td>N/A</td>
</tr>
</table>

With this representation, most fields map onto standard internet e-mail headers.  Two concepts which are present in EVE mail but which are not represented in internal e-mail representations are message status (e.g. read, unread, etc.) and categorization (e.g. inbox folders).  Instead, these concepts are covered by internet e-mail transport protocols.

### IMAP Representation

IMAP has a natural mapping for both message state and categorization.  We can use the "\Seen" IMAP message attribute to represent the "read" EVE mail flag.  We can interpret EVE mail labels as designating folders.  This interepretation has a few consequences:

1. All EVE mail will be in the IMAP INBOX folder by default (similar to the EVE mail in-game client).
2. An EVE mail with label "foo" will appear in an IMAP folder named "foo" (as well as appearing in INBOX).
3. An EVE mail with multiple labels will appear in all IMAP folders which correspond to those labels.
4. Removing mail from an IMAP folder will remove the corresponding EVE mail label.  Conversely, moving mail to an IMAP folder will add a corresponding EVE mail label.
5. Creating an IMAP folder will create a new EVE mail label.
6. Other attributes of EVE mail labels (e.g. color) will be ignored.

### POP3 Representation

POP3 is a simple transport protocol intended to download e-mail from a server to a local client.  As such, POP3 lacks the capability to model message status and categorization.  As an aid for such clients, we will populate the "Keywords" e-mail header with label names.  This will allow some clients to support searches similar to the mechanism used to organize EVE mail by label.

## Sending and Receiving EVE Mail

Sending and receiving EVE mail is achieved using an internet e-mail client as configured above.  Incoming EVE mail will be mapped to an internet e-mail structure as described in the previous section.  In particular:

1. Character, corporation and alliance IDs are mapped to human-readable names using standard ESI services (e.g. alliance, character and corporation names search).
2. E-mail addresses are automatically created for character, corporation and alliance recipients using the naming scheme `"<name>" <<id>@<type>.evemail>` where `<type` is one of `char`, `corp`, `alliance` or `ml`.
3. A unique internet e-mail ID is created for each incoming e-mail using the naming scheme `<<account_char_id>_<mail_id>@evemail>`.
4. Labels attached to incoming EVE mail are mapped to human-readable IMAP folder names or keywords using standard ESI services (e.g. character_mail_labels endpoint).

The manner in which EVE mail is retrieved from the Orbital server depends on the client type.  IMAP clients will typically fetch a copy of e-mail on demand, retaining the original e-mail on the server.  POP3 clients will fetch a copy of some or all available e-mail that hasn't been retrieved yet, and may also be configured to delete e-mail from the server after it has been retrieved.  As described above, we recommend that you NOT configure you POP3 client to automatically delete retrieved e-mail.

Sending an EVE mail consists of specifying one or more target e-mail addresses using the naming scheme described above.  Since the sender may not know the appropriate entity ID, an alternative address may be used of the form `<"entity name"@<type>.evemail>` where `<type>` is one of `char`, `corp`, `alliance` or `ml`.  In this form, the server will resolve the ID of the named entity based on the supplied name.  If no such entity is found, then the mail will "bounce" and be marked undeliverable for the specified address.

The sender may specify destination addresses in `To:`, `CC:` and `BCC:` header fields.  Note, however, that EVE mail does not have a concept of "carbon copy" so that addresses specified in this manner are simply added to the recipient list.

Message fields other than `To:`, `CC:`, `BCC:`, `Subject:` and the message body are ignored.  If the message body is multi-part MIME, then only the first part is included; all other parts are dropped.

The Orbital SMTP server will only forward to EVE mail addresses.  All other addresses are ignored.

