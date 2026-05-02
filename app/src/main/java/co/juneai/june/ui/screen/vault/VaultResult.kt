package co.juneai.june.ui.screen.vault

sealed class VaultResult<out T> {
    data class Success<T>(val data: T) : VaultResult<T>()
    data class Error(val error: VaultException) : VaultResult<Nothing>()

    inline fun onSuccess(action: (T) -> Unit): VaultResult<T> {
        if (this is Success) action(data)
        return this
    }

    inline fun onError(action: (VaultException) -> Unit): VaultResult<T> {
        if (this is Error) action(error)
        return this
    }

    fun getOrNull(): T? = (this as? Success)?.data
}