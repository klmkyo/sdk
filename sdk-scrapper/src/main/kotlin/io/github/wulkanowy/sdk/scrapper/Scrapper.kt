package io.github.wulkanowy.sdk.scrapper

import io.github.wulkanowy.sdk.scrapper.attendance.Absent
import io.github.wulkanowy.sdk.scrapper.exception.ScrapperException
import io.github.wulkanowy.sdk.scrapper.login.LoginHelper
import io.github.wulkanowy.sdk.scrapper.messages.Folder
import io.github.wulkanowy.sdk.scrapper.messages.Message
import io.github.wulkanowy.sdk.scrapper.messages.Recipient
import io.github.wulkanowy.sdk.scrapper.repository.AccountRepository
import io.github.wulkanowy.sdk.scrapper.repository.HomepageRepository
import io.github.wulkanowy.sdk.scrapper.repository.MessagesRepository
import io.github.wulkanowy.sdk.scrapper.repository.RegisterRepository
import io.github.wulkanowy.sdk.scrapper.repository.StudentRepository
import io.github.wulkanowy.sdk.scrapper.repository.StudentStartRepository
import io.github.wulkanowy.sdk.scrapper.service.ServiceManager
import okhttp3.Interceptor
import okhttp3.logging.HttpLoggingInterceptor
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime

class Scrapper private constructor(
    val logLevel: HttpLoggingInterceptor.Level,
    val androidVersion: String,
    val buildTag: String,
    val ssl: Boolean,
    val host: String,
    val loginType: LoginType,
    val symbol: String,
    val email: String,
    val password: String,
    val schoolSymbol: String,
    val studentId: Int,
    val classId: Int,
    val diaryId: Int,
    val schoolYear: Int,
    val emptyCookieJarInterceptor: Boolean,
    val appInterceptors: List<Pair<Interceptor, Boolean>>
) {

    class Builder {
        var logLevel: HttpLoggingInterceptor.Level = HttpLoggingInterceptor.Level.BASIC
        var androidVersion: String = "5.1"
        var buildTag: String = "SM-J500H Build/LMY48B"
        var scrapperBaseUrl: String = "https://fakelog.cf"
            set(value) {
                ssl = value.startsWith("https")
                host = URL(value).let { "${it.host}:${it.port}".removeSuffix(":-1") }
                field = value
            }
        var host: String = ""
        var loginType: LoginType = LoginType.AUTO
        var symbol: String = "Default"
        var email: String = ""
        var ssl: Boolean = false // todo
        var password: String = ""
        var schoolSymbol: String = ""
        var studentId: Int = 0
        var classId: Int = 0
        var diaryId: Int = 0
        var schoolYear: Int = 0
        var emptyCookieJarInterceptor: Boolean = false
        var appInterceptors: List<Pair<Interceptor, Boolean>> = emptyList()

        fun build(action: Builder.() -> Unit): Scrapper {
            action()
            return build()
        }

        fun build() = Scrapper(
            logLevel = logLevel,
            androidVersion = androidVersion,
            buildTag = buildTag,
            ssl = ssl,
            host = host,
            loginType = loginType,
            symbol = symbol,
            email = email,
            password = password,
            schoolSymbol = schoolSymbol,
            classId = classId,
            diaryId = diaryId,
            schoolYear = schoolYear,
            studentId = studentId,
            emptyCookieJarInterceptor = emptyCookieJarInterceptor,
            appInterceptors = appInterceptors
        )
    }

    // TODO: refactor
    enum class LoginType {
        AUTO,
        STANDARD,
        ADFS,
        ADFSCards,
        ADFSLight,
        ADFSLightScoped,
        ADFSLightCufs
    }

    private val schema get() = "http" + if (ssl) "s" else ""
    private val normalizedSymbol get() = if (symbol.isBlank()) "Default" else symbol.getNormalizedSymbol()
    private val okHttpFactory by lazy { OkHttpClientBuilderFactory() }

    private val serviceManager
        get() = ServiceManager(
            okHttpClientBuilderFactory = okHttpFactory,
            logLevel = logLevel,
            loginType = loginType,
            schema = schema,
            host = host,
            symbol = normalizedSymbol,
            email = email,
            password = password,
            schoolSymbol = schoolSymbol,
            studentId = studentId,
            diaryId = diaryId,
            schoolYear = schoolYear,
            androidVersion = androidVersion,
            buildTag = buildTag,
            emptyCookieJarIntercept = emptyCookieJarInterceptor
        ).apply {
            appInterceptors.forEach { (interceptor, isNetwork) ->
                setInterceptor(interceptor, isNetwork)
            }
        }

    private val account by lazy {
        AccountRepository(serviceManager.getAccountService())
    }

    // todo: maybe inside getStudents()?
    private val register by lazy {
        RegisterRepository(
            normalizedSymbol, email, password,
            LoginHelper(loginType, schema, host, normalizedSymbol, serviceManager.getCookieManager(), serviceManager.getLoginService()),
            serviceManager.getRegisterService(),
            serviceManager.getMessagesService(withLogin = false),
            serviceManager.getStudentService(withLogin = false, studentInterceptor = false),
            serviceManager.urlGenerator
        )
    }

    private val studentStart by lazy {
        StudentStartRepository(
            studentId = studentId.takeIf { it != 0 } ?: throw ScrapperException("Student id is not set"),
            classId = classId.takeIf { it != 0 } ?: throw ScrapperException("Class id is not set"),
            api = serviceManager.getStudentService(withLogin = true, studentInterceptor = false)
        )
    }

    private val student by lazy {
        StudentRepository(serviceManager.getStudentService())
    }

    private val messages by lazy {
        MessagesRepository(serviceManager.getMessagesService())
    }

    private val homepage by lazy {
        HomepageRepository(serviceManager.getHomepageService())
    }

    suspend fun getPasswordResetCaptcha(registerBaseUrl: String, symbol: String) = account.getPasswordResetCaptcha(registerBaseUrl, symbol)

    suspend fun sendPasswordResetRequest(registerBaseUrl: String, symbol: String, email: String, captchaCode: String): String {
        return account.sendPasswordResetRequest(registerBaseUrl, symbol, email.trim(), captchaCode)
    }

    suspend fun getStudents() = register.getStudents()

    suspend fun getSemesters() = studentStart.getSemesters()

    suspend fun getAttendance(startDate: LocalDate, endDate: LocalDate? = null) = student.getAttendance(startDate, endDate)

    suspend fun getAttendanceSummary(subjectId: Int? = -1) = student.getAttendanceSummary(subjectId)

    suspend fun excuseForAbsence(absents: List<Absent>, content: String? = null) = student.excuseForAbsence(absents, content)

    suspend fun getSubjects() = student.getSubjects()

    suspend fun getExams(startDate: LocalDate, endDate: LocalDate? = null) = student.getExams(startDate, endDate)

    suspend fun getGrades(semesterId: Int) = student.getGrades(semesterId)

    suspend fun getGradesDetails(semesterId: Int? = null) = student.getGradesDetails(semesterId)

    suspend fun getGradesSummary(semesterId: Int? = null) = student.getGradesSummary(semesterId)

    suspend fun getGradesPartialStatistics(semesterId: Int) = student.getGradesPartialStatistics(semesterId)

    suspend fun getGradesPointsStatistics(semesterId: Int) = student.getGradesPointsStatistics(semesterId)

    suspend fun getGradesSemesterStatistics(semesterId: Int) = student.getGradesAnnualStatistics(semesterId)

    suspend fun getHomework(startDate: LocalDate, endDate: LocalDate? = null) = student.getHomework(startDate, endDate)

    suspend fun getNotes() = student.getNotes()

    suspend fun getConferences() = student.getConferences()

    suspend fun getTimetable(startDate: LocalDate, endDate: LocalDate? = null) = student.getTimetable(startDate, endDate)

    suspend fun getTimetableNormal(startDate: LocalDate, endDate: LocalDate? = null) = student.getTimetableNormal(startDate, endDate)

    suspend fun getTimetableAdditional(startDate: LocalDate) = student.getTimetableAdditional(startDate)

    suspend fun getCompletedLessons(startDate: LocalDate, endDate: LocalDate? = null, subjectId: Int = -1) = student.getCompletedLessons(startDate, endDate, subjectId)

    suspend fun getRegisteredDevices() = student.getRegisteredDevices()

    suspend fun getToken() = student.getToken()

    suspend fun unregisterDevice(id: Int) = student.unregisterDevice(id)

    suspend fun getTeachers() = student.getTeachers()

    suspend fun getSchool() = student.getSchool()

    suspend fun getStudentInfo() = student.getStudentInfo()

    suspend fun getReportingUnits() = messages.getReportingUnits()

    suspend fun getRecipients(unitId: Int, role: Int = 2) = messages.getRecipients(unitId, role)

    suspend fun getMessages(
        folder: Folder,
        startDate: LocalDateTime? = null,
        endDate: LocalDateTime? = null
    ): List<Message> {
        return when (folder) {
            Folder.RECEIVED -> messages.getReceivedMessages(startDate, endDate)
            Folder.SENT -> messages.getSentMessages(startDate, endDate)
            Folder.TRASHED -> messages.getDeletedMessages(startDate, endDate)
        }
    }

    suspend fun getReceivedMessages(startDate: LocalDateTime? = null, endDate: LocalDateTime? = null) = messages.getReceivedMessages(startDate, endDate)

    suspend fun getSentMessages(startDate: LocalDateTime? = null, endDate: LocalDateTime? = null) = messages.getSentMessages(startDate, endDate)

    suspend fun getDeletedMessages(startDate: LocalDateTime? = null, endDate: LocalDateTime? = null) = messages.getDeletedMessages(startDate, endDate)

    suspend fun getMessageRecipients(messageId: Int, loginId: Int = 0) = messages.getMessageRecipients(messageId, loginId)

    suspend fun getMessageDetails(messageId: Int, folderId: Int, read: Boolean = false, id: Int? = null) = messages.getMessageDetails(messageId, folderId, read, id)

    suspend fun getMessageContent(messageId: Int, folderId: Int, read: Boolean = false, id: Int? = null) = messages.getMessage(messageId, folderId, read, id)

    suspend fun sendMessage(subject: String, content: String, recipients: List<Recipient>) = messages.sendMessage(subject, content, recipients)

    suspend fun deleteMessages(messagesToDelete: List<Int>, folderId: Int) = messages.deleteMessages(messagesToDelete, folderId)

    suspend fun getDirectorInformation() = homepage.getDirectorInformation()

    suspend fun getSelfGovernments() = homepage.getSelfGovernments()

    suspend fun getStudentThreats() = homepage.getStudentThreats()

    suspend fun getStudentsTrips() = homepage.getStudentsTrips()

    suspend fun getLastGrades() = homepage.getLastGrades()

    suspend fun getFreeDays() = homepage.getFreeDays()

    suspend fun getKidsLuckyNumbers() = homepage.getKidsLuckyNumbers()

    suspend fun getKidsLessonPlan() = homepage.getKidsLessonPlan()

    suspend fun getLastHomework() = homepage.getLastHomework()

    suspend fun getLastTests() = homepage.getLastTests()

    suspend fun getLastStudentLessons() = homepage.getLastStudentLessons()
}
