package com.minibank.card.web;

import com.minibank.card.dto.CardControlsRequest;
import com.minibank.card.dto.CardDetailsResponse;
import com.minibank.card.dto.CardResponse;
import com.minibank.card.dto.CardSpendingResponse;
import com.minibank.card.dto.CreateCardRequest;
import com.minibank.card.dto.UpdateCardLimitsRequest;
import com.minibank.card.dto.UpdateCardRequest;
import com.minibank.card.service.CardService;
import com.minibank.common.error.ApiError;
import com.minibank.common.security.AuthenticatedUser;
import com.minibank.common.security.SecurityUtils;
import com.minibank.transfer.domain.TransferStatus;
import com.minibank.transfer.dto.TransactionResponse;
import com.minibank.transfer.service.TransferQueryService;
import com.minibank.common.web.PageResponse;
import com.minibank.common.web.SortWhitelist;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The caller's cards.
 *
 * <p>Every route resolves the card through {@link CardService}, which returns {@code
 * CARD_NOT_FOUND} for anything the caller does not own. The controller never compares ids itself,
 * and there is no administrative route into this resource at all.
 */
@RestController
@RequestMapping("/api/v1/cards")
@Tag(name = "Cards", description = "Issuing, freezing and managing payment cards")
public class CardController {

    private static final Set<String> SORTABLE = Set.of("createdAt", "amount", "status", "completedAt");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private final CardService cardService;
    private final TransferQueryService transferQueryService;
    private final Clock clock;

    public CardController(CardService cardService, TransferQueryService transferQueryService, Clock clock) {
        this.cardService = cardService;
        this.transferQueryService = transferQueryService;
        this.clock = clock;
    }

    @GetMapping
    @Operation(summary = "List my cards", description = "Newest first. Cancelled cards are omitted.")
    public ResponseEntity<List<CardResponse>> listCards() {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        var now = clock.instant();
        return ResponseEntity.ok(cardService.listOwnedCards(caller.getId()).stream()
                .map(card -> CardResponse.from(card, now))
                .toList());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one of my cards")
    @ApiResponse(responseCode = "404", description = "No such card, or it belongs to somebody else")
    public ResponseEntity<CardResponse> getCard(@PathVariable UUID id) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok(CardResponse.from(cardService.getOwnCard(id, caller), clock.instant()));
    }

    @PostMapping
    @Operation(
            summary = "Order a card",
            description = "Issues a physical or virtual card against one of your accounts. "
                    + "The number is generated locally and is not a payment credential.")
    @ApiResponse(responseCode = "201", description = "Card issued")
    @ApiResponse(
            responseCode = "422",
            description = "You already hold the maximum number of cards",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<CardResponse> createCard(@Valid @RequestBody CreateCardRequest request) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        var card = cardService.issue(request, caller);
        return ResponseEntity.status(HttpStatus.CREATED).body(CardResponse.from(card, clock.instant()));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Rename a card or change its design")
    public ResponseEntity<CardResponse> updateCard(
            @PathVariable UUID id, @Valid @RequestBody UpdateCardRequest request) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok(CardResponse.from(cardService.update(id, request, caller), clock.instant()));
    }

    @PatchMapping("/{id}/freeze")
    @Operation(
            summary = "Freeze a card",
            description = "Blocks the card immediately. Reversible, unlike cancelling it.")
    public ResponseEntity<CardResponse> freezeCard(@PathVariable UUID id) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok(CardResponse.from(cardService.freeze(id, caller), clock.instant()));
    }

    @PatchMapping("/{id}/unfreeze")
    @Operation(summary = "Unfreeze a card")
    public ResponseEntity<CardResponse> unfreezeCard(@PathVariable UUID id) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok(CardResponse.from(cardService.unfreeze(id, caller), clock.instant()));
    }

    @PatchMapping("/{id}/limits")
    @Operation(
            summary = "Set the card's spending limits",
            description = "Omit a value to leave it unchanged; set the matching clear flag to remove a cap.")
    public ResponseEntity<CardResponse> updateLimits(
            @PathVariable UUID id, @Valid @RequestBody UpdateCardLimitsRequest request) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok(CardResponse.from(cardService.updateLimits(id, request, caller), clock.instant()));
    }

    @PatchMapping("/{id}/controls")
    @Operation(
            summary = "Turn the card's usage channels on or off",
            description = "Omit a switch to leave it unchanged. Enforced server-side on every card operation.")
    public ResponseEntity<CardResponse> updateControls(
            @PathVariable UUID id, @Valid @RequestBody CardControlsRequest request) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok(CardResponse.from(cardService.updateControls(id, request, caller), clock.instant()));
    }

    @GetMapping("/{id}/spending")
    @Operation(
            summary = "Limit usage for a card",
            description = "Outgoing settled amounts on the attached account, today and this month.")
    public ResponseEntity<CardSpendingResponse> spending(@PathVariable UUID id) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok(cardService.spending(id, caller));
    }

    @PostMapping("/{id}/details")
    @Operation(
            summary = "Reveal the full card details",
            description = "Returns the decrypted number and a derived verification value. "
                    + "POST rather than GET so the request cannot be triggered by a link, cached by "
                    + "an intermediary or replayed from browser history. Every call is audited. "
                    + "The values are simulated: they authorise nothing anywhere.")
    @ApiResponse(responseCode = "200", description = "Details revealed and the reveal recorded")
    @ApiResponse(
            responseCode = "409",
            description = "The card is expired or cancelled",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<CardDetailsResponse> revealDetails(@PathVariable UUID id) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok()
                // Belt and braces: nothing between here and the browser should keep a copy.
                .cacheControl(CacheControl.noStore())
                .body(cardService.revealDetails(id, caller));
    }

    @GetMapping("/{id}/transactions")
    @Operation(
            summary = "Transactions for the account behind a card",
            description = "MBank has no card acquiring, so this is the attached account's history "
                    + "rather than a separate stream of card authorisations.")
    public ResponseEntity<PageResponse<TransactionResponse>> cardTransactions(
            @PathVariable UUID id,
            @RequestParam(required = false) TransferStatus status,
            @PageableDefault(size = 20) Pageable pageable) {

        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        UUID accountId = cardService.getOwnCard(id, caller).getAccount().getId();
        var filter = TransferQueryService.TransferFilter.of(status, null, null, null, null);
        var page = transferQueryService.searchForAccount(
                accountId, caller, filter, SortWhitelist.sanitize(pageable, SORTABLE, DEFAULT_SORT));
        return ResponseEntity.ok(
                PageResponse.from(page, transfer -> TransactionResponse.from(transfer, Set.of(accountId))));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancel a card", description = "Permanent. A cancelled card is never reactivated.")
    @ApiResponse(responseCode = "204", description = "Card cancelled")
    public ResponseEntity<Void> cancelCard(@PathVariable UUID id) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        cardService.cancel(id, caller);
        return ResponseEntity.noContent().build();
    }
}
