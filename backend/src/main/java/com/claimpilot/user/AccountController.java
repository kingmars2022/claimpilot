package com.claimpilot.user;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final AccountService accounts;
    private final CurrentUserService currentUser;

    public AccountController(AccountService accounts, CurrentUserService currentUser) {
        this.accounts = accounts;
        this.currentUser = currentUser;
    }

    /**
     * Deletes the account and every piece of data about it. The token stops working at once. 204 when
     * everything is gone; 202 when part of the clean-up was interrupted and finishes in the background.
     */
    @DeleteMapping
    public ResponseEntity<Void> delete() {
        return accounts.deleteEverything(currentUser.get())
                ? ResponseEntity.noContent().build()
                : ResponseEntity.accepted().build();
    }
}
