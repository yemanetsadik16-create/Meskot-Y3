const { describe, it, before, after, beforeEach } = require('node:test');
const fs = require('node:fs');
const {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails
} = require('@firebase/rules-unit-testing');

const PROJECT_ID = 'gen-lang-client-0975870281';
const DATABASE_ID = 'ai-studio-android-meskotro-aa905bbb-3d87-4d82-bd16-4cc70947b90f';

let testEnv;

function ts() {
  return new Date(Date.now() - 1000);
}

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      host: '127.0.0.1',
      port: 8085,
      rules: fs.readFileSync('firestore.rules', 'utf8')
    }
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  await testEnv.clearFirestore();
});

function authedDb(uid) {
  return testEnv.authenticatedContext(uid).firestore(DATABASE_ID);
}

function unauthedDb() {
  return testEnv.unauthenticatedContext().firestore(DATABASE_ID);
}

describe('Meskot Firestore Security Rules', () => {
  it('denies unauthenticated reads and writes across collections', async () => {
    const db = unauthedDb();
    await assertFails(db.collection('public_profiles').where('visibility', '==', 'PUBLIC').get());
    await assertFails(db.collection('posts').where('privacy', '==', 'PUBLIC').get());
    await assertFails(db.collection('users').doc('alice').get());
  });

  it('enforces public_profiles creation, update, and public directory listing without PII', async () => {
    const alice = authedDb('alice');
    const bob = authedDb('bob');

    await assertSucceeds(
      alice.collection('public_profiles').doc('alice').set({
        displayName: 'Alice Kebede',
        bio: 'Addis coffee lover',
        location: 'Addis Ababa, Ethiopia',
        avatarColorHex: 0xFFC93E03,
        online: true,
        verified: false,
        stars: 0,
        visibility: 'PUBLIC',
        createdAt: ts(alice),
        updatedAt: ts(alice)
      })
    );

    // Bob can list public profiles matching visibility == PUBLIC
    await assertSucceeds(
      bob.collection('public_profiles').where('visibility', '==', 'PUBLIC').get()
    );

    // Bob cannot update Alice's public profile
    await assertFails(
      bob.collection('public_profiles').doc('alice').update({
        displayName: 'Hacked',
        updatedAt: ts(bob)
      })
    );

    // Alice can update allowed profile fields
    await assertSucceeds(
      alice.collection('public_profiles').doc('alice').update({
        displayName: 'Alice K.',
        bio: 'Updated bio',
        location: 'Hawassa',
        updatedAt: ts(alice)
      })
    );

    // Alice cannot tamper with stars or verified
    await assertFails(
      alice.collection('public_profiles').doc('alice').update({
        verified: true,
        updatedAt: ts(alice)
      })
    );
  });

  it('enforces owner-only private users/{userId} document and blocks listing', async () => {
    const alice = authedDb('alice');
    const bob = authedDb('bob');

    await assertSucceeds(
      alice.collection('users').doc('alice').set({
        email: 'alice@example.com',
        walletEtb: 0.0,
        friendIds: [],
        savedPostIds: [],
        hiddenPostIds: [],
        savedListingIds: [],
        createdAt: ts(alice),
        updatedAt: ts(alice)
      })
    );

    await assertSucceeds(alice.collection('users').doc('alice').get());
    await assertFails(bob.collection('users').doc('alice').get());
    await assertFails(alice.collection('users').get());

    await assertSucceeds(
      alice.collection('users').doc('alice').update({
        savedPostIds: ['post_1'],
        updatedAt: ts(alice)
      })
    );
  });

  it('enforces posts creation, public/owner list queries, reactions, comments, edit, and delete', async () => {
    const alice = authedDb('alice');
    const bob = authedDb('bob');

    await assertSucceeds(
      alice.collection('posts').doc('post_1').set({
        authorId: 'alice',
        authorName: 'Alice',
        text: 'Selam Meskot!',
        privacy: 'PUBLIC',
        bgColorIndex: 0,
        reactions: {},
        comments: [],
        shareCount: 0,
        createdAt: ts(alice),
        updatedAt: ts(alice)
      })
    );

    // Spoofed authorId must fail
    await assertFails(
      bob.collection('posts').doc('post_spoof').set({
        authorId: 'alice',
        authorName: 'Alice',
        text: 'Spoofed',
        privacy: 'PUBLIC',
        bgColorIndex: 0,
        reactions: {},
        comments: [],
        shareCount: 0,
        createdAt: ts(bob),
        updatedAt: ts(bob)
      })
    );

    // Public list query succeeds
    await assertSucceeds(bob.collection('posts').where('privacy', '==', 'PUBLIC').get());
    // Author list query succeeds
    await assertSucceeds(alice.collection('posts').where('authorId', '==', 'alice').get());

    // Bob can react and comment on public post
    await assertSucceeds(
      bob.collection('posts').doc('post_1').update({
        reactions: { bob: 'LIKE' },
        updatedAt: ts(bob)
      })
    );

    // Bob cannot edit Alice's post text
    await assertFails(
      bob.collection('posts').doc('post_1').update({
        text: 'Hijacked',
        updatedAt: ts(bob)
      })
    );

    // Alice can edit and delete her post
    await assertSucceeds(
      alice.collection('posts').doc('post_1').update({
        text: 'Updated Selam!',
        updatedAt: ts(alice)
      })
    );
    await assertFails(bob.collection('posts').doc('post_1').delete());
    await assertSucceeds(alice.collection('posts').doc('post_1').delete());
  });

  it('enforces participant-scoped friend_requests and conversations/messages', async () => {
    const alice = authedDb('alice');
    const bob = authedDb('bob');
    const charlie = authedDb('charlie');

    // Friend request
    await assertSucceeds(
      alice.collection('friend_requests').doc('alice_bob').set({
        fromUid: 'alice',
        toUid: 'bob',
        participantUids: ['alice', 'bob'],
        createdAt: ts(alice)
      })
    );
    await assertSucceeds(
      bob.collection('friend_requests').where('participantUids', 'array-contains', 'bob').get()
    );
    await assertFails(charlie.collection('friend_requests').doc('alice_bob').get());
    await assertSucceeds(bob.collection('friend_requests').doc('alice_bob').delete());

    // Conversation & messages
    await assertSucceeds(
      alice.collection('conversations').doc('alice_bob').set({
        participantIds: ['alice', 'bob'],
        lastMessage: '',
        createdAt: ts(alice),
        updatedAt: ts(alice)
      })
    );
    await assertFails(charlie.collection('conversations').doc('alice_bob').get());

    await assertSucceeds(
      alice.collection('conversations').doc('alice_bob').collection('messages').doc('m1').set({
        senderId: 'alice',
        participantIds: ['alice', 'bob'],
        text: 'Selam Bob!',
        edited: false,
        createdAt: ts(alice),
        updatedAt: ts(alice)
      })
    );

    await assertSucceeds(
      bob.collection('conversations').doc('alice_bob')
        .collection('messages')
        .where('participantIds', 'array-contains', 'bob')
        .get()
    );
    await assertFails(
      charlie.collection('conversations').doc('alice_bob').collection('messages').doc('m1').get()
    );
    await assertFails(
      bob.collection('conversations').doc('alice_bob').collection('messages').doc('m1').delete()
    );
    await assertSucceeds(
      alice.collection('conversations').doc('alice_bob').collection('messages').doc('m1').update({
        text: 'Selam Bob!!',
        edited: true,
        updatedAt: ts(alice)
      })
    );
  });

  it('enforces stories, groups, listings, reels, albums, notifications, and ad_campaigns', async () => {
    const alice = authedDb('alice');
    const bob = authedDb('bob');

    await assertSucceeds(
      alice.collection('stories').doc('s1').set({
        authorId: 'alice',
        authorName: 'Alice',
        caption: 'Morning buna',
        bgColorHex: 0xFFEE4C04,
        live: false,
        visibility: 'PUBLIC',
        createdAt: ts(alice)
      })
    );
    await assertSucceeds(bob.collection('stories').where('visibility', '==', 'PUBLIC').get());

    await assertSucceeds(
      alice.collection('groups').doc('g1').set({
        name: 'Addis Builders',
        description: 'Tech community',
        coverColorHex: 0xFFC93E03,
        creatorId: 'alice',
        memberIds: ['alice'],
        visibility: 'PUBLIC',
        createdAt: ts(alice),
        updatedAt: ts(alice)
      })
    );
    await assertSucceeds(
      bob.collection('groups').doc('g1').update({
        memberIds: ['alice', 'bob'],
        updatedAt: ts(bob)
      })
    );

    await assertSucceeds(
      alice.collection('listings').doc('l1').set({
        sellerId: 'alice',
        sellerName: 'Alice',
        title: 'Yirgacheffe Beans 1kg',
        priceEtb: 1200,
        category: 'Coffee',
        city: 'Addis Ababa',
        description: 'Freshly roasted',
        visibility: 'PUBLIC',
        createdAt: ts(alice)
      })
    );
    await assertSucceeds(bob.collection('listings').where('visibility', '==', 'PUBLIC').get());

    await assertSucceeds(
      alice.collection('reels').doc('r1').set({
        authorId: 'alice',
        authorName: 'Alice',
        caption: 'Sunset over Entoto',
        likedBy: [],
        comments: [],
        visibility: 'PUBLIC',
        createdAt: ts(alice),
        updatedAt: ts(alice)
      })
    );
    await assertSucceeds(
      bob.collection('reels').doc('r1').update({
        likedBy: ['bob'],
        updatedAt: ts(bob)
      })
    );

    // Owner-scoped subcollections
    await assertSucceeds(
      alice.collection('users').doc('alice').collection('albums').doc('a1').set({
        ownerId: 'alice',
        title: 'Holiday',
        photoUris: [],
        createdAt: ts(alice)
      })
    );
    await assertFails(bob.collection('users').doc('alice').collection('albums').doc('a1').get());

    await assertSucceeds(
      alice.collection('users').doc('alice').collection('ad_campaigns').doc('ad1').set({
        ownerId: 'alice',
        title: 'Coffee Promo',
        objective: 'Traffic',
        budgetEtb: 500,
        days: 7,
        impressions: 0,
        clicks: 0,
        active: true,
        createdAt: ts(alice)
      })
    );
    await assertFails(bob.collection('users').doc('alice').collection('ad_campaigns').doc('ad1').get());

    // Notification from bob to alice
    await assertSucceeds(
      bob.collection('users').doc('alice').collection('notifications').doc('n1').set({
        actorId: 'bob',
        actorName: 'Bob',
        type: 'friend_req',
        targetId: 'bob',
        messageEn: 'sent you a friend request',
        messageAm: 'የጓደኝነት ጥያቄ ላከልዎት',
        read: false,
        createdAt: ts(bob)
      })
    );
    await assertSucceeds(alice.collection('users').doc('alice').collection('notifications').get());
    await assertFails(bob.collection('users').doc('alice').collection('notifications').get());
  });
});
