package io.github.wulkanowy.api

import io.github.wulkanowy.api.repository.*
import io.github.wulkanowy.api.service.ServiceManager
import okhttp3.Interceptor
import okhttp3.logging.HttpLoggingInterceptor
import org.threeten.bp.LocalDate
import org.threeten.bp.LocalDateTime

class Api {

    private val changeManager = resettableManager()

    var logLevel: HttpLoggingInterceptor.Level = HttpLoggingInterceptor.Level.BASIC
        set(value) {
            if (field != value) changeManager.reset()
            field = value
        }

    var ssl: Boolean = true
        set(value) {
            if (field != value) changeManager.reset()
            field = value
        }

    var host: String = "fakelog.cf"
        set(value) {
            if (field != value) changeManager.reset()
            field = value
        }

    var loginType: LoginType = LoginType.AUTO
        set(value) {
            if (field != value) changeManager.reset()
            field = value
        }

    var symbol: String = "Default"
        set(value) {
            if (field != value) changeManager.reset()
            field = value
        }

    var email: String = ""
        set(value) {
            if (field != value) changeManager.reset()
            field = value
        }

    var password: String = ""
        set(value) {
            if (field != value) changeManager.reset()
            field = value
        }

    var schoolSymbol: String = ""
        set(value) {
            if (field != value) changeManager.reset()
            field = value
        }

    var studentId: Int = 0
        set(value) {
            if (field != value) changeManager.reset()
            field = value
        }

    var diaryId: Int = 0
        set(value) {
            if (field != value) changeManager.reset()
            field = value
        }

    var useNewStudent: Boolean = false

    enum class LoginType {
        AUTO,
        STANDARD,
        ADFS,
        ADFSCards,
        ADFSLight
    }

    private val appInterceptors: MutableMap<Int, Pair<Interceptor, Boolean>> = mutableMapOf()

    @Deprecated("redundant call, will be deleted in feature")
    fun notifyDataChanged() {}

    fun setInterceptor(interceptor: Interceptor, network: Boolean = false, index: Int = -1) {
        appInterceptors[index] = Pair(interceptor, network)
    }

    private val schema by resettableLazy(changeManager) { "http" + if (ssl) "s" else "" }

    private val normalizedSymbol by resettableLazy(changeManager) { if (symbol.isBlank()) "Default" else symbol }

    private val serviceManager by resettableLazy(changeManager) {
        ServiceManager(logLevel, loginType, schema, host, normalizedSymbol, email, password, schoolSymbol, studentId, diaryId).apply {
            appInterceptors.forEach {
                setInterceptor(it.value.first, it.value.second, it.key)
            }
        }
    }

    private val register by resettableLazy(changeManager) {
        RegisterRepository(normalizedSymbol, email, password,
                LoginRepository(loginType, schema, host, normalizedSymbol, serviceManager.getCookieManager(), serviceManager.getLoginService()),
                serviceManager.getRegisterService(),
                serviceManager.getSnpService(false, false)
        )
    }

    private val snpStart by resettableLazy(changeManager) {
        if (0 == studentId) throw ApiException("Student id is not set")
        StudentAndParentStartRepository(normalizedSymbol, schoolSymbol, studentId, serviceManager.getSnpService(true, false))
    }

    private val snp by resettableLazy(changeManager) {
        StudentAndParentRepository(serviceManager.getSnpService())
    }

    private val student by resettableLazy(changeManager) {
        StudentRepository(serviceManager.getStudentService())
    }

    private val messages by resettableLazy(changeManager) {
        MessagesRepository(studentId, serviceManager.getMessagesService())
    }

    fun getPupils() = register.getPupils()

    fun getSemesters() = snpStart.getSemesters()

    fun getCurrentSemester() = snpStart.getCurrentSemester()

    fun getAttendance(startDate: LocalDate, endDate: LocalDate? = null) = snp.getAttendance(startDate, endDate)

    fun getAttendanceSummary(subjectId: Int? = null) = snp.getAttendanceSummary(subjectId)

    fun getSubjects() = snp.getSubjects()

    fun getExams(startDate: LocalDate, endDate: LocalDate? = null) = snp.getExams(startDate, endDate)

    fun getGrades(semesterId: Int? = null) = if (useNewStudent) student.getGrades(semesterId) else snp.getGrades(semesterId)

    fun getGradesSummary(semesterId: Int? = null) = snp.getGradesSummary(semesterId)

    fun getGradesStatistics(semesterId: Int? = null, annual: Boolean = false) = snp.getGradesStatistics(semesterId, annual)

    fun getHomework(startDate: LocalDate, endDate: LocalDate? = null) = snp.getHomework(startDate, endDate)

    fun getNotes() = snp.getNotes()

    fun getRegisteredDevices() = if (useNewStudent) student.getRegisteredDevices() else snp.getRegisteredDevices()

    fun getToken() = snp.getToken()

    fun unregisterDevice(id: Int) = snp.unregisterDevice(id)

    fun getTeachers() = snp.getTeachers()

    fun getStudentInfo() = snp.getStudentInfo()

    fun getReportingUnits() = messages.getReportingUnits()

    fun getRecipients(role: Int = 2) = messages.getRecipients(role)

    fun getReceivedMessages(startDate: LocalDateTime? = null, endDate: LocalDateTime? = null) = messages.getReceivedMessages(startDate, endDate)

    fun getSentMessages(startDate: LocalDateTime? = null, endDate: LocalDateTime? = null) = messages.getSentMessages(startDate, endDate)

    fun getDeletedMessages(startDate: LocalDateTime? = null, endDate: LocalDateTime? = null) = messages.getDeletedMessages(startDate, endDate)

    fun getMessage(messageId: Int, folderId: Int, read: Boolean = false, id: Int? = null) = messages.getMessage(messageId, folderId, read, id)

    fun getTimetable(startDate: LocalDate, endDate: LocalDate? = null) = snp.getTimetable(startDate, endDate)

    fun getRealized(startDate: LocalDate? = null) = snp.getRealized(startDate)
}
