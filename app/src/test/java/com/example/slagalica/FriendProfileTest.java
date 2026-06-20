package com.example.slagalica;

import com.example.slagalica.model.FriendProfile;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FriendProfileTest {

    @Test
    public void canInvite_loggedInFriendWhoIsNotInMatch() {
        FriendProfile friend = new FriendProfile();
        friend.loggedIn = true;

        assertTrue(friend.canInvite());
    }

    @Test
    public void cannotInvite_loggedOutFriend() {
        FriendProfile friend = new FriendProfile();

        assertFalse(friend.canInvite());
    }

    @Test
    public void cannotInvite_friendWhoIsInMatch() {
        FriendProfile friend = new FriendProfile();
        friend.loggedIn = true;
        friend.inMatch = true;
        friend.appActive = true;

        assertFalse(friend.canInvite());
    }
}
