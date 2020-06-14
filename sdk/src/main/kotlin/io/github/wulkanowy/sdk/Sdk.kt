package io.github.wulkanowy.sdk

import io.github.wulkanowy.sdk.exception.FeatureNotAvailableException
import io.github.wulkanowy.sdk.exception.ScrapperExceptionTransformer
import io.github.wulkanowy.sdk.mapper.*
import io.github.wulkanowy.sdk.mobile.Mobile
import io.github.wulkanowy.sdk.pojo.*
import io.github.wulkanowy.sdk.scrapper.Scrapper
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.Single
import kotlinx.coroutines.rx2.rxSingle
import okhttp3.Interceptor
import okhttp3.logging.HttpLoggingInterceptor
import org.threeten.bp.LocalDate
import org.threeten.bp.LocalDateTime

class Sdk {

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

    private val mobile = Mobile()

    private val scrapper = Scrapper()

    var mode = Mode.HYBRID

    var mobileBaseUrl = ""
        set(value) {
            field = value
            mobile.baseUrl = value
        }

    var certKey = ""
        set(value) {
            field = value
            mobile.certKey = value
        }

    var privateKey = ""
        set(value) {
            field = value
            mobile.privateKey = privateKey
        }

    var scrapperBaseUrl = ""
        set(value) {
            field = value
            scrapper.baseUrl = value
        }

    var email = ""
        set(value) {
            field = value
            scrapper.email = value
        }

    var password = ""
        set(value) {
            field = value
            scrapper.password = value
        }

    var schoolSymbol = ""
        set(value) {
            field = value
            scrapper.schoolSymbol = value
            mobile.schoolSymbol = value
        }

    var classId = 0
        set(value) {
            field = value
            scrapper.classId = value
            mobile.classId = value
        }

    var studentId = 0
        set(value) {
            field = value
            scrapper.studentId = value
            mobile.studentId = value
        }

    var loginId = 0
        set(value) {
            field = value
            mobile.loginId = value
        }

    var diaryId = 0
        set(value) {
            field = value
            scrapper.diaryId = value
        }

    var schoolYear = 0
        set(value) {
            field = value
            scrapper.schoolYear = value
        }

    var symbol = ""
        set(value) {
            field = value
            scrapper.symbol = value
        }

    var loginType = ScrapperLoginType.AUTO
        set(value) {
            field = value
            scrapper.loginType = Scrapper.LoginType.valueOf(value.name)
        }

    var logLevel = HttpLoggingInterceptor.Level.BASIC
        set(value) {
            field = value
            scrapper.logLevel = value
            mobile.logLevel = value
        }

    var androidVersion = "8.1.0"
        set(value) {
            field = value
            scrapper.androidVersion = value
        }

    var buildTag = "SM-J500H Build/LMY48B"
        set(value) {
            field = value
            scrapper.buildTag = value
        }

    var emptyCookieJarInterceptor: Boolean = false
        set(value) {
            field = value
            scrapper.emptyCookieJarInterceptor = value
        }

    private val interceptors: MutableList<Pair<Interceptor, Boolean>> = mutableListOf()

    fun setSimpleHttpLogger(logger: (String) -> Unit) {
        logLevel = HttpLoggingInterceptor.Level.NONE
        addInterceptor(HttpLoggingInterceptor(HttpLoggingInterceptor.Logger {
            logger(it)
        }).setLevel(HttpLoggingInterceptor.Level.BASIC))
    }

    fun addInterceptor(interceptor: Interceptor, network: Boolean = false) {
        scrapper.addInterceptor(interceptor, network)
        mobile.setInterceptor(interceptor, network)
        interceptors.add(interceptor to network)
    }

    fun switchDiary(diaryId: Int, schoolYear: Int): Sdk {
        return also {
            it.diaryId = diaryId
            it.schoolYear = schoolYear
        }
    }

    fun getPasswordResetCaptchaCode(registerBaseUrl: String, symbol: String) = rxSingle { scrapper.getPasswordResetCaptcha(registerBaseUrl, symbol) }

    fun sendPasswordResetRequest(registerBaseUrl: String, symbol: String, email: String, captchaCode: String): Single<String> {
        return rxSingle { scrapper.sendPasswordResetRequest(registerBaseUrl, symbol, email, captchaCode) }
    }

    fun getStudentsFromMobileApi(token: String, pin: String, symbol: String, firebaseToken: String, apiKey: String = ""): Single<List<Student>> {
        return rxSingle { mobile.getCertificate(token, pin, symbol, buildTag, androidVersion, firebaseToken) }
            .flatMap { rxSingle { mobile.getStudents(it, apiKey) } }
            .map { it.mapStudents(symbol) }
    }

    fun getStudentsFromScrapper(email: String, password: String, scrapperBaseUrl: String, symbol: String = "Default"): Single<List<Student>> {
        return scrapper.let {
            it.baseUrl = scrapperBaseUrl
            it.email = email
            it.password = password
            it.symbol = symbol
            rxSingle { it.getStudents() }.compose(ScrapperExceptionTransformer()).map { students -> students.mapStudents() }
        }
    }

    fun getStudentsHybrid(email: String, password: String, scrapperBaseUrl: String, firebaseToken: String, startSymbol: String = "Default", apiKey: String = ""): Single<List<Student>> {
        return getStudentsFromScrapper(email, password, scrapperBaseUrl, startSymbol)
            .compose(ScrapperExceptionTransformer())
            .map { students -> students.distinctBy { it.symbol } }
            .flatMapObservable { Observable.fromIterable(it) }
            .flatMapSingle { scrapperStudent ->
                scrapper.let {
                    it.symbol = scrapperStudent.symbol
                    it.schoolSymbol = scrapperStudent.schoolSymbol
                    it.studentId = scrapperStudent.studentId
                    it.diaryId = -1
                    it.classId = scrapperStudent.classId
                    it.loginType = Scrapper.LoginType.valueOf(scrapperStudent.loginType.name)
                }
                rxSingle { scrapper.getToken() }.compose(ScrapperExceptionTransformer())
                    .flatMap { getStudentsFromMobileApi(it.token, it.pin, it.symbol, firebaseToken, apiKey) }
                    .map { apiStudents ->
                        apiStudents.map { student ->
                            student.copy(
                                loginMode = Mode.HYBRID,
                                loginType = scrapperStudent.loginType,
                                scrapperBaseUrl = scrapperStudent.scrapperBaseUrl
                            )
                        }
                    }
            }.toList().map { it.flatten() }
    }

    fun getSemesters(now: LocalDate = LocalDate.now()): Single<List<Semester>> {
        return when (mode) {
            Mode.HYBRID, Mode.SCRAPPER -> rxSingle { scrapper.getSemesters() }.compose(ScrapperExceptionTransformer()).map { it.mapSemesters() }
            Mode.API -> rxSingle { mobile.getStudents().mapSemesters(studentId, now) }
        }
    }

    fun getAttendance(startDate: LocalDate, endDate: LocalDate, semesterId: Int): Single<List<Attendance>> {
        return when (mode) {
            Mode.SCRAPPER -> rxSingle { scrapper.getAttendance(startDate, endDate) }.compose(ScrapperExceptionTransformer()).map { it.mapAttendance() }
            Mode.HYBRID, Mode.API -> rxSingle { mobile.getAttendance(startDate, endDate, semesterId).mapAttendance(mobile.getDictionaries()) }
        }
    }

    fun getAttendanceSummary(subjectId: Int? = -1): Single<List<AttendanceSummary>> {
        return when (mode) {
            Mode.HYBRID, Mode.SCRAPPER -> rxSingle { scrapper.getAttendanceSummary(subjectId) }.compose(ScrapperExceptionTransformer()).map { it.mapAttendanceSummary() }
            Mode.API -> throw FeatureNotAvailableException("Attendance summary is not available in API mode")
        }
    }

    fun excuseForAbsence(absents: List<Absent>, content: String? = null): Single<Boolean> {
        return when (mode) {
            Mode.HYBRID, Mode.SCRAPPER -> rxSingle { scrapper.excuseForAbsence(absents.mapToScrapperAbsent(), content) }.compose(ScrapperExceptionTransformer())
            Mode.API -> throw FeatureNotAvailableException("Absence excusing is not available in API mode")
        }
    }

    fun getSubjects(): Single<List<Subject>> {
        return when (mode) {
            Mode.HYBRID, Mode.SCRAPPER -> rxSingle { scrapper.getSubjects() }.compose(ScrapperExceptionTransformer()).map { it.mapSubjects() }
            Mode.API -> rxSingle { mobile.getDictionaries().subjects.mapSubjects() }
        }
    }

    fun getExams(start: LocalDate, end: LocalDate, semesterId: Int): Single<List<Exam>> {
        return when (mode) {
            Mode.SCRAPPER -> rxSingle { scrapper.getExams(start, end) }.compose(ScrapperExceptionTransformer()).map { it.mapExams() }
            Mode.HYBRID, Mode.API -> rxSingle { mobile.getExams(start, end, semesterId).mapExams(mobile.getDictionaries()) }
        }
    }

    fun getGrades(semesterId: Int): Single<Pair<List<Grade>, List<GradeSummary>>> {
        return when (mode) {
            Mode.SCRAPPER -> rxSingle { scrapper.getGrades(semesterId) }.compose(ScrapperExceptionTransformer()).map { grades -> grades.mapGrades() }
            Mode.HYBRID, Mode.API -> rxSingle { mobile.getGrades(semesterId).mapGrades(mobile.getDictionaries()) }
        }
    }

    fun getGradesDetails(semesterId: Int): Single<List<Grade>> {
        return when (mode) {
            Mode.SCRAPPER -> rxSingle { scrapper.getGradesDetails(semesterId) }.compose(ScrapperExceptionTransformer()).map { grades -> grades.mapGradesDetails() }
            Mode.HYBRID, Mode.API -> rxSingle { mobile.getGradesDetails(semesterId).mapGradesDetails(mobile.getDictionaries()) }
        }
    }

    fun getGradesSummary(semesterId: Int): Single<List<GradeSummary>> {
        return when (mode) {
            Mode.SCRAPPER -> rxSingle { scrapper.getGradesSummary(semesterId) }.compose(ScrapperExceptionTransformer()).map { it.mapGradesSummary() }
            Mode.HYBRID, Mode.API -> rxSingle { mobile.getGradesSummary(semesterId).mapGradesSummary(mobile.getDictionaries()) }
        }
    }

    fun getGradesAnnualStatistics(semesterId: Int): Single<List<GradeStatistics>> {
        return when (mode) {
            Mode.HYBRID, Mode.SCRAPPER -> rxSingle { scrapper.getGradesAnnualStatistics(semesterId) }.compose(ScrapperExceptionTransformer()).map { it.mapGradeStatistics() }
            Mode.API -> throw FeatureNotAvailableException("Class grades annual statistics is not available in API mode")
        }
    }

    fun getGradesPartialStatistics(semesterId: Int): Single<List<GradeStatistics>> {
        return when (mode) {
            Mode.HYBRID, Mode.SCRAPPER -> rxSingle { scrapper.getGradesPartialStatistics(semesterId) }.compose(ScrapperExceptionTransformer()).map { it.mapGradeStatistics() }
            Mode.API -> throw FeatureNotAvailableException("Class grades partial statistics is not available in API mode")
        }
    }

    fun getGradesPointsStatistics(semesterId: Int): Single<List<GradePointsStatistics>> {
        return when (mode) {
            Mode.HYBRID, Mode.SCRAPPER -> rxSingle { scrapper.getGradesPointsStatistics(semesterId) }.compose(ScrapperExceptionTransformer()).map { it.mapGradePointsStatistics() }
            Mode.API -> throw FeatureNotAvailableException("Class grades points statistics is not available in API mode")
        }
    }

    fun getHomework(start: LocalDate, end: LocalDate, semesterId: Int = 0): Single<List<Homework>> {
        return when (mode) {
            Mode.SCRAPPER -> rxSingle { scrapper.getHomework(start, end) }.compose(ScrapperExceptionTransformer()).map { it.mapHomework() }
            Mode.HYBRID, Mode.API -> rxSingle { mobile.getHomework(start, end, semesterId).mapHomework(mobile.getDictionaries()) }
        }
    }

    fun getNotes(semesterId: Int): Single<List<Note>> {
        return when (mode) {
            Mode.SCRAPPER -> rxSingle { scrapper.getNotes() }.compose(ScrapperExceptionTransformer()).map { it.mapNotes() }
            Mode.HYBRID, Mode.API -> rxSingle { mobile.getNotes(semesterId).mapNotes(mobile.getDictionaries()) }
        }
    }

    fun getRegisteredDevices(): Single<List<Device>> {
        return when (mode) {
            Mode.HYBRID, Mode.SCRAPPER -> rxSingle { scrapper.getRegisteredDevices() }.compose(ScrapperExceptionTransformer()).map { it.mapDevices() }
            Mode.API -> throw FeatureNotAvailableException("Devices management is not available in API mode")
        }
    }

    fun getToken(): Single<Token> {
        return when (mode) {
            Mode.HYBRID, Mode.SCRAPPER -> rxSingle { scrapper.getToken() }.compose(ScrapperExceptionTransformer()).map { it.mapToken() }
            Mode.API -> throw FeatureNotAvailableException("Devices management is not available in API mode")
        }
    }

    fun unregisterDevice(id: Int): Single<Boolean> {
        return when (mode) {
            Mode.HYBRID, Mode.SCRAPPER -> rxSingle { scrapper.unregisterDevice(id) }.compose(ScrapperExceptionTransformer())
            Mode.API -> throw FeatureNotAvailableException("Devices management is not available in API mode")
        }
    }

    fun getTeachers(semesterId: Int): Single<List<Teacher>> {
        return when (mode) {
            Mode.SCRAPPER -> rxSingle { scrapper.getTeachers() }.compose(ScrapperExceptionTransformer()).map { it.mapTeachers() }
            Mode.HYBRID, Mode.API -> rxSingle { mobile.getTeachers(studentId, semesterId).mapTeachers(mobile.getDictionaries()) }
        }
    }

    fun getSchool(): Single<School> {
        return when (mode) {
            Mode.HYBRID, Mode.SCRAPPER -> rxSingle { scrapper.getSchool() }.compose(ScrapperExceptionTransformer()).map { it.mapSchool() }
            Mode.API -> throw FeatureNotAvailableException("School info is not available in API mode")
        }
    }

    fun getStudentInfo(): Single<StudentInfo> {
        return when (mode) {
            Mode.HYBRID, Mode.SCRAPPER -> rxSingle { scrapper.getStudentInfo() }.compose(ScrapperExceptionTransformer()).map { it.mapStudent() }
            Mode.API -> throw FeatureNotAvailableException("Student info is not available in API mode")
        }
    }

    fun getReportingUnits(): Single<List<ReportingUnit>> {
        return when (mode) {
            Mode.HYBRID, Mode.SCRAPPER -> rxSingle { scrapper.getReportingUnits() }.compose(ScrapperExceptionTransformer()).map { it.mapReportingUnits() }
            Mode.API -> rxSingle { mobile.getStudents().mapReportingUnits(studentId) }
        }
    }

    fun getRecipients(unitId: Int, role: Int = 2): Single<List<Recipient>> {
        return when (mode) {
            Mode.HYBRID, Mode.SCRAPPER -> rxSingle { scrapper.getRecipients(unitId, role) }.compose(ScrapperExceptionTransformer()).map { it.mapRecipients() }
            Mode.API -> rxSingle { mobile.getDictionaries().teachers.mapRecipients(unitId) }
        }
    }

    fun getMessages(folder: Folder, start: LocalDateTime, end: LocalDateTime): Single<List<Message>> {
        return when (folder) {
            Folder.RECEIVED -> getReceivedMessages(start, end)
            Folder.SENT -> getSentMessages(start, end)
            Folder.TRASHED -> getDeletedMessages(start, end)
        }
    }

    fun getReceivedMessages(start: LocalDateTime, end: LocalDateTime): Single<List<Message>> {
        return when (mode) {
            Mode.HYBRID, Mode.SCRAPPER -> rxSingle { scrapper.getReceivedMessages() }.compose(ScrapperExceptionTransformer()).map { it.mapMessages() } // TODO
            Mode.API -> rxSingle { mobile.getMessages(start, end).mapMessages(mobile.getDictionaries()) }
        }
    }

    fun getSentMessages(start: LocalDateTime, end: LocalDateTime): Single<List<Message>> {
        return when (mode) {
            Mode.HYBRID, Mode.SCRAPPER -> rxSingle { scrapper.getSentMessages() }.compose(ScrapperExceptionTransformer()).map { it.mapMessages() }
            Mode.API -> rxSingle { mobile.getMessagesSent(start, end).mapMessages(mobile.getDictionaries()) }
        }
    }

    fun getDeletedMessages(start: LocalDateTime, end: LocalDateTime): Single<List<Message>> {
        return when (mode) {
            Mode.HYBRID, Mode.SCRAPPER -> rxSingle { scrapper.getDeletedMessages() }.compose(ScrapperExceptionTransformer()).map { it.mapMessages() }
            Mode.API -> rxSingle { mobile.getMessagesDeleted(start, end).mapMessages(mobile.getDictionaries()) }
        }
    }

    fun getMessageRecipients(messageId: Int, senderId: Int): Single<List<Recipient>> {
        return when (mode) {
            Mode.HYBRID, Mode.SCRAPPER -> rxSingle { scrapper.getMessageRecipients(messageId, senderId) }.compose(ScrapperExceptionTransformer()).map { it.mapRecipients() }
            Mode.API -> TODO()
        }
    }

    fun getMessageDetails(messageId: Int, folderId: Int, read: Boolean = false, id: Int? = null): Single<MessageDetails> {
        return when (mode) {
            Mode.HYBRID, Mode.SCRAPPER -> rxSingle { scrapper.getMessageDetails(messageId, folderId, read, id) }.compose(ScrapperExceptionTransformer()).map { it.mapScrapperMessage() }
            Mode.API -> rxSingle {
                mobile.changeMessageStatus(messageId, when (folderId) {
                    1 -> "Odebrane"
                    2 -> "Wysłane"
                    else -> "Usunięte"
                }, "Widoczna").let { MessageDetails("", emptyList()) }
            }
        }
    }

    fun sendMessage(subject: String, content: String, recipients: List<Recipient>): Single<SentMessage> {
        return when (mode) {
            Mode.HYBRID, Mode.SCRAPPER -> rxSingle { scrapper.sendMessage(subject, content, recipients.mapFromRecipientsToScraper()) }
                .compose(ScrapperExceptionTransformer())
                .map { it.mapSentMessage() }
            Mode.API -> rxSingle { mobile.sendMessage(subject, content, recipients.mapFromRecipientsToMobile()).mapSentMessage(loginId) }
        }
    }

    fun deleteMessages(messages: List<Pair<Int, Int>>): Single<Boolean> {
        return when (mode) {
            Mode.SCRAPPER -> rxSingle { scrapper.deleteMessages(messages) }.compose(ScrapperExceptionTransformer())
            Mode.HYBRID, Mode.API -> Completable.mergeDelayError(messages.map { (messageId, folderId) ->
                rxSingle {
                    mobile.changeMessageStatus(messageId, when (folderId) {
                        1 -> "Odebrane"
                        2 -> "Wysłane"
                        else -> "Usunięte"
                    }, "Usunieta")
                }.ignoreElement()
            }).toSingleDefault(true)
        }
    }

    fun getTimetable(start: LocalDate, end: LocalDate): Single<List<Timetable>> {
        return when (mode) {
            Mode.SCRAPPER -> rxSingle { scrapper.getTimetable(start, end) }.compose(ScrapperExceptionTransformer()).map { it.mapTimetable() }
            Mode.HYBRID, Mode.API -> rxSingle { mobile.getTimetable(start, end, 0).mapTimetable(mobile.getDictionaries()) }
        }
    }

    fun getCompletedLessons(start: LocalDate, end: LocalDate? = null, subjectId: Int = -1): Single<List<CompletedLesson>> {
        return when (mode) {
            Mode.HYBRID, Mode.SCRAPPER -> rxSingle { scrapper.getCompletedLessons(start, end, subjectId) }.compose(ScrapperExceptionTransformer()).map { it.mapCompletedLessons() }
            Mode.API -> throw FeatureNotAvailableException("Completed lessons are not available in API mode")
        }
    }

    fun getLuckyNumber(unitName: String = ""): Maybe<Int> {
        return getKidsLuckyNumbers().filter { it.isNotEmpty() }.flatMap { numbers ->
            // if lucky number unitName match unit name from student tile
            numbers.singleOrNull { number -> number.unitName == unitName }?.let {
                return@flatMap Maybe.just(it)
            }

            // if there there is only one lucky number and its doesn't match to any student
            if (numbers.size == 1) {
                return@flatMap Maybe.just(numbers.single())
            }

            // if there is more than one lucky number, return first (just like this was working before 0.16.0)
            if (numbers.size > 1) {
                return@flatMap Maybe.just(numbers.first())
            }

            // else
            Maybe.empty<LuckyNumber>()
        }.map { it.number }
    }

    fun getSelfGovernments(): Single<List<GovernmentUnit>> {
        return when (mode) {
            Mode.HYBRID, Mode.SCRAPPER -> rxSingle { scrapper.getSelfGovernments() }.compose(ScrapperExceptionTransformer()).map { it.mapToUnits() }
            Mode.API -> throw FeatureNotAvailableException("Self governments is not available in API mode")
        }
    }

    fun getStudentThreats(): Single<List<String>> {
        return when (mode) {
            Mode.HYBRID, Mode.SCRAPPER -> rxSingle { scrapper.getStudentThreats() }.compose(ScrapperExceptionTransformer())
            Mode.API -> throw FeatureNotAvailableException("Student threats are not available in API mode")
        }
    }

    fun getStudentsTrips(): Single<List<String>> {
        return when (mode) {
            Mode.HYBRID, Mode.SCRAPPER -> rxSingle { scrapper.getStudentsTrips() }.compose(ScrapperExceptionTransformer())
            Mode.API -> throw FeatureNotAvailableException("Students trips is not available in API mode")
        }
    }

    fun getLastGrades(): Single<List<String>> {
        return when (mode) {
            Mode.HYBRID, Mode.SCRAPPER -> rxSingle { scrapper.getLastGrades() }.compose(ScrapperExceptionTransformer())
            Mode.API -> throw FeatureNotAvailableException("Last grades is not available in API mode")
        }
    }

    fun getFreeDays(): Single<List<String>> {
        return when (mode) {
            Mode.HYBRID, Mode.SCRAPPER -> rxSingle { scrapper.getFreeDays() }.compose(ScrapperExceptionTransformer())
            Mode.API -> throw FeatureNotAvailableException("Free days is not available in API mode")
        }
    }

    fun getKidsLuckyNumbers(): Single<List<LuckyNumber>> {
        return when (mode) {
            Mode.HYBRID, Mode.SCRAPPER -> rxSingle { scrapper.getKidsLuckyNumbers() }.compose(ScrapperExceptionTransformer()).map { it.mapLuckyNumbers() }
            Mode.API -> throw FeatureNotAvailableException("Kids Lucky number is not available in API mode")
        }
    }

    fun getKidsTimetable(): Single<List<String>> {
        return when (mode) {
            Mode.HYBRID, Mode.SCRAPPER -> rxSingle { scrapper.getKidsLessonPlan() }.compose(ScrapperExceptionTransformer())
            Mode.API -> throw FeatureNotAvailableException("Kids timetable is not available in API mode")
        }
    }

    fun getLastHomework(): Single<List<String>> {
        return when (mode) {
            Mode.HYBRID, Mode.SCRAPPER -> rxSingle { scrapper.getLastHomework() }.compose(ScrapperExceptionTransformer())
            Mode.API -> throw FeatureNotAvailableException("Last homework is not available in API mode")
        }
    }

    fun getLastExams(): Single<List<String>> {
        return when (mode) {
            Mode.HYBRID, Mode.SCRAPPER -> rxSingle { scrapper.getLastTests() }.compose(ScrapperExceptionTransformer())
            Mode.API -> throw FeatureNotAvailableException("Last exams is not available in API mode")
        }
    }

    fun getLastStudentLessons(): Single<List<String>> {
        return when (mode) {
            Mode.HYBRID, Mode.SCRAPPER -> rxSingle { scrapper.getLastStudentLessons() }.compose(ScrapperExceptionTransformer())
            Mode.API -> throw FeatureNotAvailableException("Last student lesson is not available in API mode")
        }
    }
}
