package com.nbs.kmm.sample.android.utils

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.lifecycle.MutableLiveData
import com.nbs.kmm.sample.android.R
import com.nbs.kmm.sample.android.utils.data.Resource
import com.nbs.kmm.sample.domain.base.ApiError
import com.nbs.kmm.sample.domain.base.ErrorCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.retryWhen
import java.net.UnknownHostException

suspend fun <U> proceed(
    outputLiveData: MutableLiveData<Resource<U>>,
    block: suspend () -> Flow<U>
) {
    block.invoke()
        .retryWhen { cause, attempt ->
            cause is ApiError && cause.errorCode == ErrorCode.ERROR_SESSION_EXPIRED && attempt == 0L
        }
        .catch { cause: Throwable ->
            if (cause is ApiError) {
                outputLiveData.value = Resource.fail(cause, cause.errorMessage)
            } else if (cause is UnknownHostException) {
                if (!isNetworkConnected())
                    outputLiveData.value = Resource.fail(
                        NoConnectionException(ErrorCode.ERROR_NO_CONNECTION),
                        ErrorCode.DEFAULT_ERROR_MESSAGE
                    )
                else
                    outputLiveData.value = Resource.fail(cause, ErrorCode.DEFAULT_ERROR_MESSAGE)
            } else {
                outputLiveData.value = Resource.fail(cause, ErrorCode.DEFAULT_ERROR_MESSAGE)
            }
        }.collect {
            outputLiveData.value = Resource.success(it)
        }
}

@Composable
fun ComposableObserver(observe: @Composable () -> Unit) {
    observe()
}

@Composable
inline fun <reified T> ComposableObserver(
    state: State<Resource<T>?>,
    crossinline onLoading: @Composable () -> Unit,
    crossinline onSuccess: @Composable (T) -> Unit,
    crossinline onFailure: @Composable (ApiError) -> Unit
) {
    ComposableObserver {
        when (state.value) {
            is Resource.Loading -> {
                onLoading.invoke()
            }
            is Resource.Success -> {
                onSuccess.invoke(getValue(state.value))
            }
            is Resource.Failure -> {
                onFailure.invoke(observeApiError(state.value))
            }
            else -> {}
        }
    }
}

inline fun <reified T> getValue(resource: Resource<T>?): T {
    val successResource = resource as Resource.Success
    return successResource.data
}

fun <T> observeApiError(resources: Resource<T>?): ApiError {
    val resource = resources as Resource.Failure<T>

    when (resource.throwable) {
        is ApiError -> {
            return resource.throwable
        }
        is NoConnectionException -> {
            return ApiError(
                ErrorCode.ERROR_NO_CONNECTION,
                ContextProvider.get().getString(R.string.error_no_connection),
                status = false
            )
        }
        else -> {
            resource.throwable?.printStackTrace()
            return ApiError(
                ErrorCode.ERROR_UNABLE_TO_REACH_SERVICE,
                ContextProvider.get().getString(R.string.error_unable_to_connect),
                status = false
            )
        }
    }
}

class NoConnectionException(
    val code: String
) : Exception()

@SuppressLint("MissingPermission")
fun isNetworkConnected(): Boolean {
    val cm =
        ContextProvider.get().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val activeNetwork = cm.activeNetworkInfo
    return activeNetwork != null && activeNetwork.isConnectedOrConnecting
}

fun showToast(message: String){
    Toast.makeText(ContextProvider.get(), message, Toast.LENGTH_SHORT).show()
}