package at.mavila.dbchatbox.domain.club.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Command record for uploading a payment proof document.
 *
 * @param memberSubscriptionId the subscription this payment document belongs to
 * @param fileName             the original file name
 * @param fileContent          Base64-encoded content of the PDF file
 * @param notes                optional notes
 * @since 2026-04-13
 */
public record UploadPaymentDocumentCommand(
    @NotNull(message = "Subscription ID is required") Long memberSubscriptionId,

    @NotBlank(message = "File name is required") @Size(max = 255, message = "File name must not exceed 255 characters") String fileName,

    @NotBlank(message = "File content is required") String fileContent,

    @Size(max = 500, message = "Notes must not exceed 500 characters") String notes) {
}
