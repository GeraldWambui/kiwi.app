package co.juneai.june.ui.screen.vault

class VaultException(
    val errorType: ErrorType,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {
    enum class ErrorType {
        SERIALIZATION, STORAGE, VALIDATION, ENCRYPTION, IO, SECURITY, NOT_FOUND
    }
}