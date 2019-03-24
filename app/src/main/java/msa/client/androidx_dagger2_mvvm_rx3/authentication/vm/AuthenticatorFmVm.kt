package msa.client.androidx_dagger2_mvvm_rx3.authentication.vm

import android.accounts.AccountAuthenticatorResponse
import android.accounts.AccountManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.preference.PreferenceManager
import androidx.core.os.bundleOf
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.f2prateek.rx.preferences2.RxSharedPreferences
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import msa.client.androidx_dagger2_mvvm_rx3.authentication.entity.MemberSignIn
import msa.client.androidx_dagger2_mvvm_rx3.authentication.entity.MemberSignUp
import msa.client.androidx_dagger2_mvvm_rx3.authentication.repo.AuthenticatorRepositoryManager
import msa.client.androidx_dagger2_mvvm_rx3.authentication.repo.UserManager
import msa.client.androidx_dagger2_mvvm_rx3.authentication.webapi.AuthenticatorApiInterface
import msa.client.androidx_dagger2_mvvm_rx3.base.cm.navi.NavigationRequest
import msa.client.androidx_dagger2_mvvm_rx3.base.data.AsyncStateLiveData
import msa.client.androidx_dagger2_mvvm_rx3.base.vm.VmForFm
import msa.client.androidx_dagger2_mvvm_rx3.base.data.AsyncState
import msa.client.androidx_dagger2_mvvm_rx3.base.data.AsyncStateLiveDataDefine
import msa.client.androidx_dagger2_mvvm_rx3.base.helper.SharedPreferencesHelper
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.Retrofit



/*
import net.samystudio.beaver.data.AsyncState
import net.samystudio.beaver.data.manager.AuthenticatorRepositoryManager
import net.samystudio.beaver.data.manager.UserManager
import net.samystudio.beaver.di.scope.FragmentScope
import net.samystudio.beaver.ext.getClassTag
import net.samystudio.beaver.ui.base.viewmodel.BaseFragmentViewModel
import net.samystudio.beaver.ui.base.viewmodel.DataPushViewModel
import net.samystudio.beaver.ui.common.navigation.NavigationRequest
import net.samystudio.beaver.ui.common.viewmodel.AsyncStateLiveData
*/


//@FragmentScope
class AuthenticatorFmVm(application: Application)// @Inject
// constructor(private val authenticatorRepositoryManager: AuthenticatorRepositoryManager)
    : VmForFm(application), AsyncStateLiveDataDefine
    {
    private val _context:Context ? = getApplication();

    private val accountManager: AccountManager = AccountManager.get(_context)
    private val sharedPreferences: SharedPreferences =  PreferenceManager.getDefaultSharedPreferences(_context)
    private val rxSharedPreferences: RxSharedPreferences = RxSharedPreferences.create(sharedPreferences)

    private val sharedPreferencesHelper: SharedPreferencesHelper= SharedPreferencesHelper(rxSharedPreferences)

    private val  userManager:UserManager = UserManager(accountManager,sharedPreferencesHelper)

    private val authenticatorRepositoryManager: AuthenticatorRepositoryManager = getAuthenticatorRepositoryManager()

// 레토르핏 통해서 구현체 가져와야...

    private fun getAuthenticatorRepositoryManager(): AuthenticatorRepositoryManager {
        val httpLoggingInterceptor : HttpLoggingInterceptor = HttpLoggingInterceptor()

        httpLoggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY)


        val client:OkHttpClient = OkHttpClient.Builder().addInterceptor(httpLoggingInterceptor).build();
        val retrofit = Retrofit.Builder()
            .baseUrl("")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .build()

        val authenticatorApiInterface: AuthenticatorApiInterface = retrofit.create(AuthenticatorApiInterface::class.java!!)

        return AuthenticatorRepositoryManager(userManager,authenticatorApiInterface)
    }


    private val _asyncStateLiveData: AsyncStateLiveData = AsyncStateLiveData()
    override val liveData: LiveData<AsyncState> =
        AsyncStateLiveData()//_asyncStateLiveData
    private val _signInVisibility: MutableLiveData<Boolean> = MutableLiveData()
    private val _signUpVisibility: MutableLiveData<Boolean> = MutableLiveData()
    val signInVisibility: LiveData<Boolean> = _signInVisibility
    val signUpVisibility: LiveData<Boolean> = _signUpVisibility
    private var authenticatorResponse: AccountAuthenticatorResponse? = null
    private lateinit var intent: Intent

   override fun handleIntent(intent: Intent) { super.handleIntent(intent)

        this.intent = intent

        authenticatorResponse =
                intent.getParcelableExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE)
        authenticatorResponse?.onRequestContinued()

        //_signInVisibility.value = !intent.hasExtra(UserManager.KEY_CREATE_ACCOUNT)
        //_signUpVisibility.value = !intent.hasExtra(UserManager.KEY_CONFIRM_ACCOUNT)
    }

    //버튼 클릭 이벤트 청취기
    fun <T : MemberSignIn> singInClickSubscriber(observable: Observable<T>) {
        addDisposable(

            observable //버튼 클릭이벤트 발생
            .flatMap {
                    memberSign -> // 발생된 맴버 정보를 꺼내서

                        _asyncStateLiveData.bind(// 처리 결과를 청취 하고 결과 발생시 postValue 수행 클랙한 수만큼 청취 가능한 바인딩이 발생
                            authenticatorRepositoryManager.signIn(memberSign) // api 호출등의 처리 결과를 구독가능한 형태로 리턴
                        )
                        .observeOn(AndroidSchedulers.mainThread())
                        .doOnNext {
                            if (it is AsyncState.Completed) handleSignInResult(memberSign) // 전역변수의 넥스트 값을 받아서 리절트 핸들링 왜이렇게 만들었는지...
                        }
            }
            .subscribe()
        )
    }

    private fun  handleSignInResult(memberSignIn: MemberSignIn) {
        authenticatorResponse?.onResult(
            bundleOf(
                AccountManager.KEY_ACCOUNT_NAME to memberSignIn.email,
                AccountManager.KEY_ACCOUNT_TYPE to UserManager.ACCOUNT_TYPE,
                AccountManager.KEY_PASSWORD to memberSignIn.password
            )
        )
        authenticatorResponse = null
        navigate(NavigationRequest.Pop())
    }
    //버튼 클릭 이벤트 청취기
    fun <T : MemberSignUp> signUpClickSubscriber(observable: Observable<T>) {
        addDisposable(
            observable.flatMap { memberSignUp ->
                        _asyncStateLiveData.bind(
                            authenticatorRepositoryManager.signUp(memberSignUp)
                        ).observeOn(AndroidSchedulers.mainThread())
                            .doOnNext {
                                if (it is AsyncState.Completed) handleSignUpResult(memberSignUp)
                            }//.zipWith(Observable.just(""), BiFunction { t1, _ -> t1 })

            }.subscribe()
        )
    }
        private fun  handleSignUpResult(memberSignUp: MemberSignUp) {
            authenticatorResponse?.onResult(
                bundleOf(
                    AccountManager.KEY_ACCOUNT_NAME to memberSignUp.email,
                    AccountManager.KEY_ACCOUNT_TYPE to UserManager.ACCOUNT_TYPE,
                    AccountManager.KEY_PASSWORD to memberSignUp.password
                )
            )
            authenticatorResponse = null
            navigate(NavigationRequest.Pop())
        }

    /*override fun onCleared() {
        super.onCleared()

        authenticatorResponse?.onError(
            AccountManager.ERROR_CODE_CANCELED,
            "Authentication was cancelled"
        )
        authenticatorResponse = null

        intent.removeExtra(UserManager.KEY_CREATE_ACCOUNT)
        intent.removeExtra(UserManager.KEY_CONFIRM_ACCOUNT)
    }*/
}