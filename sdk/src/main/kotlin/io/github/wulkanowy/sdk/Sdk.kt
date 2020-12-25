package io.github.wulkanowy.sdk

import io.github.wulkanowy.sdk.exception.FeatureNotAvailableException
import io.github.wulkanowy.sdk.mapper.mapAttendance
import io.github.wulkanowy.sdk.mapper.mapAttendanceSummary
import io.github.wulkanowy.sdk.mapper.mapCompletedLessons
import io.github.wulkanowy.sdk.mapper.mapConferences
import io.github.wulkanowy.sdk.mapper.mapDevices
import io.github.wulkanowy.sdk.mapper.mapDirectorInformation
import io.github.wulkanowy.sdk.mapper.mapExams
import io.github.wulkanowy.sdk.mapper.mapFromRecipientsToMobile
import io.github.wulkanowy.sdk.mapper.mapFromRecipientsToScraper
import io.github.wulkanowy.sdk.mapper.mapGradePointsStatistics
import io.github.wulkanowy.sdk.mapper.mapGradeStatistics
import io.github.wulkanowy.sdk.mapper.mapGrades
import io.github.wulkanowy.sdk.mapper.mapGradesDetails
import io.github.wulkanowy.sdk.mapper.mapGradesSemesterStatistics
import io.github.wulkanowy.sdk.mapper.mapGradesSummary
import io.github.wulkanowy.sdk.mapper.mapHomework
import io.github.wulkanowy.sdk.mapper.mapLuckyNumbers
import io.github.wulkanowy.sdk.mapper.mapMessages
import io.github.wulkanowy.sdk.mapper.mapNotes
import io.github.wulkanowy.sdk.mapper.mapRecipients
import io.github.wulkanowy.sdk.mapper.mapReportingUnits
import io.github.wulkanowy.sdk.mapper.mapSchool
import io.github.wulkanowy.sdk.mapper.mapScrapperMessage
import io.github.wulkanowy.sdk.mapper.mapSemesters
import io.github.wulkanowy.sdk.mapper.mapSentMessage
import io.github.wulkanowy.sdk.mapper.mapStudent
import io.github.wulkanowy.sdk.mapper.mapStudents
import io.github.wulkanowy.sdk.mapper.mapSubjects
import io.github.wulkanowy.sdk.mapper.mapTeachers
import io.github.wulkanowy.sdk.mapper.mapTimetable
import io.github.wulkanowy.sdk.mapper.mapTimetableAdditional
import io.github.wulkanowy.sdk.mapper.mapToScrapperAbsent
import io.github.wulkanowy.sdk.mapper.mapToUnits
import io.github.wulkanowy.sdk.mapper.mapToken
import io.github.wulkanowy.sdk.mobile.Mobile
import io.github.wulkanowy.sdk.pojo.Absent
import io.github.wulkanowy.sdk.pojo.Folder
import io.github.wulkanowy.sdk.pojo.MessageDetails
import io.github.wulkanowy.sdk.pojo.Recipient
import io.github.wulkanowy.sdk.scrapper.Scrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.logging.HttpLoggingInterceptor
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Optional

class Sdk private constructor(
    private val mode: Mode,
    private val mobile: Optional<Mobile>,
    private val scrapper: Optional<Scrapper>,
    private val studentId: Int,
    private val loginId: Int,
) {

    class Builder {

        private val mobile = Mobile()

        private val scrapper = Scrapper.Builder()

        var mode = Mode.HYBRID

        var mobileBaseUrl = ""
        var certKey = ""
        var privateKey = ""
        var scrapperBaseUrl = ""
        var email = ""
        var password = ""
        var schoolSymbol = ""
        var classId = 0
        var studentId = 0
        var loginId = 0
        var diaryId = 0
        var schoolYear = 0
        var symbol = ""
        var loginType = ScrapperLoginType.AUTO
        var logLevel = HttpLoggingInterceptor.Level.BASIC
        var androidVersion = "8.1.0"
        var buildTag = "SM-J500H Build/LMY48B"
        var emptyCookieJarInterceptor: Boolean = false
        val interceptors: MutableList<Pair<Interceptor, Boolean>> = mutableListOf()

        fun build() = Sdk(
            mode = mode,
            studentId = studentId,
            loginId = loginId,
            mobile = if (mode == Mode.SCRAPPER || mobileBaseUrl.isBlank()) Optional.empty() else Optional.of(mobile.also {
                it.mobileBaseUrl = mobileBaseUrl
                it.certKey = certKey
                it.privateKey = privateKey
                it.schoolSymbol = schoolSymbol
                it.classId = classId
                it.studentId = studentId
                it.logLevel = logLevel
                it.androidVersion = androidVersion
                it.loginId = loginId
                it.logLevel = logLevel
                interceptors.forEach { (interceptor, isNetwork) ->
                    it.setInterceptor(interceptor, isNetwork)
                }
            }),
            scrapper = if (mode == Mode.API || scrapperBaseUrl.isBlank()) Optional.empty() else Optional.of(scrapper.also {
                it.scrapperBaseUrl = scrapperBaseUrl
                it.email = email
                it.password = password
                it.schoolSymbol = schoolSymbol
                it.classId = classId
                it.studentId = studentId
                it.diaryId = diaryId
                it.schoolYear = schoolYear
                it.symbol = symbol
                it.loginType = Scrapper.LoginType.valueOf(loginType.name)
                it.logLevel = logLevel
                it.androidVersion = androidVersion
                it.buildTag = buildTag
                it.emptyCookieJarInterceptor = emptyCookieJarInterceptor
                it.appInterceptors = interceptors
            }.build())
        )
    }

    enum class Mode {
        API,
        SCRAPPER,
        HYBRID
    }

    enum class ScrapperLoginType {
        AUTO,
        STANDARD,
        ADFS,
        ADFSCards,
        ADFSLight,
        ADFSLightScoped,
        ADFSLightCufs
    }

    suspend fun getPasswordResetCaptchaCode(registerBaseUrl: String, symbol: String) = withContext(Dispatchers.IO) {
        scrapper.get().getPasswordResetCaptcha(registerBaseUrl, symbol)
    }

    suspend fun sendPasswordResetRequest(registerBaseUrl: String, symbol: String, email: String, captchaCode: String) = withContext(Dispatchers.IO) {
        scrapper.get().sendPasswordResetRequest(registerBaseUrl, symbol, email, captchaCode)
    }

    suspend fun getStudentsFromMobileApi(token: String, pin: String, symbol: String, firebaseToken: String, apiKey: String = "") = withContext(Dispatchers.IO) {
        Mobile().run {
            getStudents(getCertificate(token, pin, symbol, firebaseToken), apiKey).mapStudents(symbol)
        }
    }

    suspend fun getStudentsFromScrapper(email: String, password: String, scrapperBaseUrl: String, symbol: String = "Default") = withContext(Dispatchers.IO) {
        Scrapper.Builder().also {
            it.scrapperBaseUrl = scrapperBaseUrl
            it.email = email
            it.password = password
            it.symbol = symbol
        }.build().getStudents().mapStudents()
    }

    suspend fun getStudentsHybrid(
        email: String,
        password: String,
        scrapperBaseUrl: String,
        firebaseToken: String,
        startSymbol: String = "Default",
        apiKey: String = ""
    ) = withContext(Dispatchers.IO) {
        getStudentsFromScrapper(email, password, scrapperBaseUrl, startSymbol)
            .distinctBy { it.symbol }
            .map { scrapperStudent ->
                val token = Scrapper.Builder().also {
                    it.scrapperBaseUrl = scrapperBaseUrl
                    it.email = email
                    it.password = password
                    it.symbol = scrapperStudent.symbol

                    it.schoolSymbol = scrapperStudent.schoolSymbol
                    it.studentId = scrapperStudent.studentId
                    it.diaryId = -1
                    it.classId = scrapperStudent.classId
                    it.loginType = Scrapper.LoginType.valueOf(scrapperStudent.loginType.name)
                }.build().getToken()
                getStudentsFromMobileApi(token.token, token.pin, token.symbol, firebaseToken, apiKey).map { student ->
                    student.copy(
                        loginMode = Mode.HYBRID,
                        loginType = scrapperStudent.loginType,
                        scrapperBaseUrl = scrapperStudent.scrapperBaseUrl
                    )
                }
            }.toList().flatten()
    }

    suspend fun getSemesters() = withContext(Dispatchers.IO) {
        when (mode) {
            Mode.HYBRID, Mode.SCRAPPER -> scrapper.get().getSemesters().mapSemesters()
            Mode.API -> mobile.get().getStudents().mapSemesters(studentId)
        }
    }

    suspend fun getAttendance(startDate: LocalDate, endDate: LocalDate, semesterId: Int) = withContext(Dispatchers.IO) {
        when (mode) {
            Mode.SCRAPPER -> scrapper.get().getAttendance(startDate, endDate).mapAttendance()
            Mode.HYBRID, Mode.API -> mobile.get().getAttendance(startDate, endDate, semesterId).mapAttendance(mobile.get().getDictionaries())
        }
    }

    suspend fun getAttendanceSummary(subjectId: Int? = -1) = withContext(Dispatchers.IO) {
        when (mode) {
            Mode.HYBRID, Mode.SCRAPPER -> scrapper.get().getAttendanceSummary(subjectId).mapAttendanceSummary()
            Mode.API -> throw FeatureNotAvailableException("Attendance summary is not available in API mode")
        }
    }

    suspend fun excuseForAbsence(absents: List<Absent>, content: String? = null) = withContext(Dispatchers.IO) {
        when (mode) {
            Mode.HYBRID, Mode.SCRAPPER -> scrapper.get().excuseForAbsence(absents.mapToScrapperAbsent(), content)
            Mode.API -> throw FeatureNotAvailableException("Absence excusing is not available in API mode")
        }
    }

    suspend fun getSubjects() = withContext(Dispatchers.IO) {
        when (mode) {
            Mode.HYBRID, Mode.SCRAPPER -> scrapper.get().getSubjects().mapSubjects()
            Mode.API -> mobile.get().getDictionaries().subjects.mapSubjects()
        }
    }

    suspend fun getExams(start: LocalDate, end: LocalDate, semesterId: Int) = withContext(Dispatchers.IO) {
        when (mode) {
            Mode.SCRAPPER -> scrapper.get().getExams(start, end).mapExams()
            Mode.HYBRID, Mode.API -> mobile.get().getExams(start, end, semesterId).mapExams(mobile.get().getDictionaries())
        }
    }

    suspend fun getGrades(semesterId: Int) = withContext(Dispatchers.IO) {
        when (mode) {
            Mode.SCRAPPER -> scrapper.get().getGrades(semesterId).mapGrades()
            Mode.HYBRID, Mode.API -> mobile.get().getGrades(semesterId).mapGrades(mobile.get().getDictionaries())
        }
    }

    suspend fun getGradesDetails(semesterId: Int) = withContext(Dispatchers.IO) {
        when (mode) {
            Mode.SCRAPPER -> scrapper.get().getGradesDetails(semesterId).mapGradesDetails()
            Mode.HYBRID, Mode.API -> mobile.get().getGradesDetails(semesterId).mapGradesDetails(mobile.get().getDictionaries())
        }
    }

    suspend fun getGradesSummary(semesterId: Int) = withContext(Dispatchers.IO) {
        when (mode) {
            Mode.SCRAPPER -> scrapper.get().getGradesSummary(semesterId).mapGradesSummary()
            Mode.HYBRID, Mode.API -> mobile.get().getGradesSummary(semesterId).mapGradesSummary(mobile.get().getDictionaries())
        }
    }

    suspend fun getGradesSemesterStatistics(semesterId: Int) = withContext(Dispatchers.IO) {
        when (mode) {
            Mode.HYBRID, Mode.SCRAPPER -> scrapper.get().getGradesSemesterStatistics(semesterId).mapGradesSemesterStatistics()
            Mode.API -> throw FeatureNotAvailableException("Class grades annual statistics is not available in API mode")
        }
    }

    suspend fun getGradesPartialStatistics(semesterId: Int) = withContext(Dispatchers.IO) {
        when (mode) {
            Mode.HYBRID, Mode.SCRAPPER -> scrapper.get().getGradesPartialStatistics(semesterId).mapGradeStatistics()
            Mode.API -> throw FeatureNotAvailableException("Class grades partial statistics is not available in API mode")
        }
    }

    suspend fun getGradesPointsStatistics(semesterId: Int) = withContext(Dispatchers.IO) {
        when (mode) {
            Mode.HYBRID, Mode.SCRAPPER -> scrapper.get().getGradesPointsStatistics(semesterId).mapGradePointsStatistics()
            Mode.API -> throw FeatureNotAvailableException("Class grades points statistics is not available in API mode")
        }
    }

    suspend fun getHomework(start: LocalDate, end: LocalDate, semesterId: Int = 0) = withContext(Dispatchers.IO) {
        when (mode) {
            Mode.SCRAPPER -> scrapper.get().getHomework(start, end).mapHomework()
            Mode.HYBRID, Mode.API -> mobile.get().getHomework(start, end, semesterId).mapHomework(mobile.get().getDictionaries())
        }
    }

    suspend fun getNotes(semesterId: Int) = withContext(Dispatchers.IO) {
        when (mode) {
            Mode.SCRAPPER -> scrapper.get().getNotes().mapNotes()
            Mode.HYBRID, Mode.API -> mobile.get().getNotes(semesterId).mapNotes(mobile.get().getDictionaries())
        }
    }

    suspend fun getConferences() = withContext(Dispatchers.IO) {
        when (mode) {
            Mode.HYBRID, Mode.SCRAPPER -> scrapper.get().getConferences().mapConferences()
            Mode.API -> throw FeatureNotAvailableException("Conferences is not available in API mode")
        }
    }

    suspend fun getRegisteredDevices() = withContext(Dispatchers.IO) {
        when (mode) {
            Mode.HYBRID, Mode.SCRAPPER -> scrapper.get().getRegisteredDevices().mapDevices()
            Mode.API -> throw FeatureNotAvailableException("Devices management is not available in API mode")
        }
    }

    suspend fun getToken() = withContext(Dispatchers.IO) {
        when (mode) {
            Mode.HYBRID, Mode.SCRAPPER -> scrapper.get().getToken().mapToken()
            Mode.API -> throw FeatureNotAvailableException("Devices management is not available in API mode")
        }
    }

    suspend fun unregisterDevice(id: Int) = withContext(Dispatchers.IO) {
        when (mode) {
            Mode.HYBRID, Mode.SCRAPPER -> scrapper.get().unregisterDevice(id)
            Mode.API -> throw FeatureNotAvailableException("Devices management is not available in API mode")
        }
    }

    suspend fun getTeachers(semesterId: Int) = withContext(Dispatchers.IO) {
        when (mode) {
            Mode.SCRAPPER -> scrapper.get().getTeachers().mapTeachers()
            Mode.HYBRID, Mode.API -> mobile.get().getTeachers(studentId, semesterId).mapTeachers(mobile.get().getDictionaries())
        }
    }

    suspend fun getSchool() = withContext(Dispatchers.IO) {
        when (mode) {
            Mode.HYBRID, Mode.SCRAPPER -> scrapper.get().getSchool().mapSchool()
            Mode.API -> throw FeatureNotAvailableException("School info is not available in API mode")
        }
    }

    suspend fun getStudentInfo() = withContext(Dispatchers.IO) {
        when (mode) {
            Mode.HYBRID, Mode.SCRAPPER -> scrapper.get().getStudentInfo().mapStudent()
            Mode.API -> throw FeatureNotAvailableException("Student info is not available in API mode")
        }
    }

    suspend fun getReportingUnits() = withContext(Dispatchers.IO) {
        when (mode) {
            Mode.HYBRID, Mode.SCRAPPER -> scrapper.get().getReportingUnits().mapReportingUnits()
            Mode.API -> mobile.get().getStudents().mapReportingUnits(studentId)
        }
    }

    suspend fun getRecipients(unitId: Int, role: Int = 2) = withContext(Dispatchers.IO) {
        when (mode) {
            Mode.HYBRID, Mode.SCRAPPER -> scrapper.get().getRecipients(unitId, role).mapRecipients()
            Mode.API -> mobile.get().getDictionaries().teachers.mapRecipients(unitId)
        }
    }

    suspend fun getMessages(folder: Folder, start: LocalDateTime, end: LocalDateTime) = withContext(Dispatchers.IO) {
        when (folder) {
            Folder.RECEIVED -> getReceivedMessages(start, end)
            Folder.SENT -> getSentMessages(start, end)
            Folder.TRASHED -> getDeletedMessages(start, end)
        }
    }

    suspend fun getReceivedMessages(start: LocalDateTime, end: LocalDateTime) = withContext(Dispatchers.IO) {
        when (mode) {
            Mode.HYBRID, Mode.SCRAPPER -> scrapper.get().getReceivedMessages(start, end).mapMessages()
            Mode.API -> mobile.get().getMessages(start, end).mapMessages(mobile.get().getDictionaries())
        }
    }

    suspend fun getSentMessages(start: LocalDateTime, end: LocalDateTime) = withContext(Dispatchers.IO) {
        when (mode) {
            Mode.HYBRID, Mode.SCRAPPER -> scrapper.get().getSentMessages(start, end).mapMessages()
            Mode.API -> mobile.get().getMessagesSent(start, end).mapMessages(mobile.get().getDictionaries())
        }
    }

    suspend fun getDeletedMessages(start: LocalDateTime, end: LocalDateTime) = withContext(Dispatchers.IO) {
        when (mode) {
            Mode.HYBRID, Mode.SCRAPPER -> scrapper.get().getDeletedMessages(start, end).mapMessages()
            Mode.API -> mobile.get().getMessagesDeleted(start, end).mapMessages(mobile.get().getDictionaries())
        }
    }

    suspend fun getMessageRecipients(messageId: Int, senderId: Int) = withContext(Dispatchers.IO) {
        when (mode) {
            Mode.HYBRID, Mode.SCRAPPER -> scrapper.get().getMessageRecipients(messageId, senderId).mapRecipients()
            Mode.API -> TODO()
        }
    }

    suspend fun getMessageDetails(messageId: Int, folderId: Int, read: Boolean = false, id: Int? = null) = withContext(Dispatchers.IO) {
        when (mode) {
            Mode.HYBRID, Mode.SCRAPPER -> scrapper.get().getMessageDetails(messageId, folderId, read, id).mapScrapperMessage()
            Mode.API -> mobile.get().changeMessageStatus(messageId, when (folderId) {
                1 -> "Odebrane"
                2 -> "Wysłane"
                else -> "Usunięte"
            }, "Widoczna").let { MessageDetails(it, emptyList()) }
        }
    }

    suspend fun sendMessage(subject: String, content: String, recipients: List<Recipient>) = withContext(Dispatchers.IO) {
        when (mode) {
            Mode.HYBRID, Mode.SCRAPPER -> scrapper.get().sendMessage(subject, content, recipients.mapFromRecipientsToScraper()).mapSentMessage()
            Mode.API -> mobile.get().sendMessage(subject, content, recipients.mapFromRecipientsToMobile()).mapSentMessage(loginId)
        }
    }

    suspend fun deleteMessages(messages: List<Int>, folderId: Int) = withContext(Dispatchers.IO) {
        when (mode) {
            Mode.SCRAPPER -> scrapper.get().deleteMessages(messages, folderId)
            Mode.HYBRID, Mode.API -> messages.map { messageId ->
                mobile.get().changeMessageStatus(messageId, when (folderId) {
                    1 -> "Odebrane"
                    2 -> "Wysłane"
                    else -> "Usunięte"
                }, "Usunieta")
            }.isNotEmpty()
        }
    }

    suspend fun getTimetable(start: LocalDate, end: LocalDate) = withContext(Dispatchers.IO) {
        when (mode) {
            Mode.SCRAPPER -> scrapper.get().getTimetable(start, end).let { (normal, additional) -> normal.mapTimetable() to additional.mapTimetableAdditional() }
            Mode.HYBRID, Mode.API -> mobile.get().getTimetable(start, end, 0).mapTimetable(mobile.get().getDictionaries()) to emptyList()
        }
    }

    suspend fun getTimetableNormal(start: LocalDate, end: LocalDate) = withContext(Dispatchers.IO) {
        when (mode) {
            Mode.SCRAPPER -> scrapper.get().getTimetableNormal(start, end).mapTimetable()
            Mode.HYBRID, Mode.API -> mobile.get().getTimetable(start, end, 0).mapTimetable(mobile.get().getDictionaries())
        }
    }

    suspend fun getTimetableAdditional(start: LocalDate) = withContext(Dispatchers.IO) {
        when (mode) {
            Mode.SCRAPPER -> scrapper.get().getTimetableAdditional(start).mapTimetableAdditional()
            Mode.HYBRID, Mode.API -> throw FeatureNotAvailableException("Additional timetable lessons are not available in API mode")
        }
    }

    suspend fun getCompletedLessons(start: LocalDate, end: LocalDate? = null, subjectId: Int = -1) = withContext(Dispatchers.IO) {
        when (mode) {
            Mode.HYBRID, Mode.SCRAPPER -> scrapper.get().getCompletedLessons(start, end, subjectId).mapCompletedLessons()
            Mode.API -> throw FeatureNotAvailableException("Completed lessons are not available in API mode")
        }
    }

    suspend fun getLuckyNumber(unitName: String = "") = withContext(Dispatchers.IO) {
        val numbers = getKidsLuckyNumbers()
        // if lucky number unitName match unit name from student tile
        numbers.singleOrNull { number -> number.unitName == unitName }?.let {
            return@withContext it.number
        }

        // if there there is only one lucky number and its doesn't match to any student
        if (numbers.size == 1) {
            return@withContext numbers.single().number
        }

        // if there is more than one lucky number, return first (just like this was working before 0.16.0)
        if (numbers.size > 1) {
            return@withContext numbers.first().number
        }

        // else
        null
    }

    suspend fun getDirectorInformation() = withContext(Dispatchers.IO) {
        when (mode) {
            Mode.HYBRID, Mode.SCRAPPER -> scrapper.get().getDirectorInformation().mapDirectorInformation()
            Mode.API -> throw FeatureNotAvailableException("Director informations is not available in API mode")
        }
    }

    suspend fun getSelfGovernments() = withContext(Dispatchers.IO) {
        when (mode) {
            Mode.HYBRID, Mode.SCRAPPER -> scrapper.get().getSelfGovernments().mapToUnits()
            Mode.API -> throw FeatureNotAvailableException("Self governments is not available in API mode")
        }
    }

    suspend fun getStudentThreats() = withContext(Dispatchers.IO) {
        when (mode) {
            Mode.HYBRID, Mode.SCRAPPER -> scrapper.get().getStudentThreats()
            Mode.API -> throw FeatureNotAvailableException("Student threats are not available in API mode")
        }
    }

    suspend fun getStudentsTrips() = withContext(Dispatchers.IO) {
        when (mode) {
            Mode.HYBRID, Mode.SCRAPPER -> scrapper.get().getStudentsTrips()
            Mode.API -> throw FeatureNotAvailableException("Students trips is not available in API mode")
        }
    }

    suspend fun getLastGrades() = withContext(Dispatchers.IO) {
        when (mode) {
            Mode.HYBRID, Mode.SCRAPPER -> scrapper.get().getLastGrades()
            Mode.API -> throw FeatureNotAvailableException("Last grades is not available in API mode")
        }
    }

    suspend fun getFreeDays() = withContext(Dispatchers.IO) {
        when (mode) {
            Mode.HYBRID, Mode.SCRAPPER -> scrapper.get().getFreeDays()
            Mode.API -> throw FeatureNotAvailableException("Free days is not available in API mode")
        }
    }

    suspend fun getKidsLuckyNumbers() = withContext(Dispatchers.IO) {
        when (mode) {
            Mode.HYBRID, Mode.SCRAPPER -> scrapper.get().getKidsLuckyNumbers().mapLuckyNumbers()
            Mode.API -> throw FeatureNotAvailableException("Kids Lucky number is not available in API mode")
        }
    }

    suspend fun getKidsTimetable() = withContext(Dispatchers.IO) {
        when (mode) {
            Mode.HYBRID, Mode.SCRAPPER -> scrapper.get().getKidsLessonPlan()
            Mode.API -> throw FeatureNotAvailableException("Kids timetable is not available in API mode")
        }
    }

    suspend fun getLastHomework() = withContext(Dispatchers.IO) {
        when (mode) {
            Mode.HYBRID, Mode.SCRAPPER -> scrapper.get().getLastHomework()
            Mode.API -> throw FeatureNotAvailableException("Last homework is not available in API mode")
        }
    }

    suspend fun getLastExams() = withContext(Dispatchers.IO) {
        when (mode) {
            Mode.HYBRID, Mode.SCRAPPER -> scrapper.get().getLastTests()
            Mode.API -> throw FeatureNotAvailableException("Last exams is not available in API mode")
        }
    }

    suspend fun getLastStudentLessons() = withContext(Dispatchers.IO) {
        when (mode) {
            Mode.HYBRID, Mode.SCRAPPER -> scrapper.get().getLastStudentLessons()
            Mode.API -> throw FeatureNotAvailableException("Last student lesson is not available in API mode")
        }
    }
}
