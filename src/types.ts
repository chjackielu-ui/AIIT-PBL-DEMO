export type Role='student'|'teacher'|'admin'
export interface User {id:string;name:string;role:Role;classId?:string;number?:string;contact?:string;enabled:boolean;hasPassword?:boolean;permissions?:string[];avatarVersion?:number;bio?:string;interests?:string[];techStack?:string[]}
export interface BaseClass {id:string;name:string;college?:string;major?:string}
export interface Course {id:string;name:string;status?:string;plannedStart?:string;plannedEnd?:string;actualStart?:string;actualEnd?:string}
export interface CourseDimension {id:string;name:string;weight:number;source:'manual'|'project'}
export interface GradingRule {dimensions?:CourseDimension[];attendance?:number;homework?:number;project?:number}
export interface CourseGrade {classId:string;dimensions:CourseDimension[];rows:{studentId:string;scores:Record<string,number>;total:number|null}[]}
export interface TeachingClass extends BaseClass {courseId:string;teacherIds:string[];academicYear:string;semester:string;gradingRule?:GradingRule;resources?:ProjectFile[];syllabus?:ProjectFile;courseDescription?:string}
export interface DeliverableRequirement {id:string;title:string;extensions:string[];minFiles:number;maxFiles:number;required:boolean}
export interface RubricPoint {id:string;dimension:string;title:string;description:string;maxPoints:number}
export interface ScoreReviewer {name:string;weight:number;score?:number;points:Record<string,number>;comment:string}
export interface GradingDetails {score?:number;reviewers:ScoreReviewer[];rubric?:RubricPoint[];formula?:string;recordedBy?:string}
export interface Milestone {parentId?:string;scoreWeight?:number;rubric?:RubricPoint[];submissionMode?:'shared'|'individual';deliverables?:DeliverableRequirement[];scoringRule?:string;id:string;name:string;description:string;due:string;materials:string;criteria:string;isFinal?:boolean;requireMaterials?:boolean;requiresSubmission?:boolean}
export interface CourseTask {taskKind?:'training'|'exercise';projectType?:'individual'|'group';created?:string;startAt?:string;id:string;name:string;teacherId:string;courseId:string;classId:string;description:string;crossClass:boolean;min:number;max:number;teamDue:string;due:string;status:string;milestones:Milestone[]}
export interface WorkItem {id:string;title:string;ownerId:string;status:string;due:string;source:string}
export interface ProjectFile {id:string;name:string;category:string;size:number;type:string;userId:string;time:string}
export interface Message {id:string;userId:string;text:string;code:string;fileId:string;fileIds?:string[];replyToId?:string;codeName?:string;codeLanguage?:string;time:string}
export interface Submission {grading?:GradingDetails;submissionMode?:'shared'|'individual';submitterId?:string;deliverables?:Record<string,string[]>;id:string;milestoneId:string;description:string;fileIds:string[];status:string;score?:number;comment:string;time:string;reviewerId?:string;reviewTime?:string}
export interface Contribution {userId:string;role:string;description:string;percent:number;evidence:string;score?:number;comment?:string}
export interface FinalResult {submitTime?:string;grading?:GradingDetails;submitterId?:string;deliverables?:Record<string,string[]>;summary:string;description:string;fileIds:string[];imageIds:string[];link:string;contributions:Contribution[];expertName:string;expertOrg:string;expertScore?:number;expertComment:string;projectScore?:number;comment:string;approved:boolean;reviewerId?:string;reviewTime?:string}
export interface Project {projectType?:'individual'|'group';id:string;taskId:string;name:string;description:string;tags:string;leaderId:string;memberIds:string[];status:string;created:string;share:boolean;shareId:string;reviewComment:string;milestoneRevision?:number;milestones?:Milestone[];items:WorkItem[];files:ProjectFile[];messages:Message[];submissions:Submission[];final?:FinalResult;finalHistory:FinalResult[];contributions:Contribution[];activity:{text:string;time:string;userId:string}[]}
export interface Notice {id:string;userId:string;title:string;text:string;path:string;read:boolean;time:string}
export interface HistoryRecord {id:string;userId:string;name:string;year:string;date:string;role:string;description:string;contribution:string;summary:string}
export interface LearningMetric {required:number;submitted:number;passed:number;scored:number;timed:number;onTime:number;uploads:number;submissionRate:number|null;completionRate:number|null;onTimeRate:number|null;averageScore:number|null}
export interface LearningAnalysis {classId:string;studentCount:number;mine:LearningMetric;classAverage:Record<string,number|null>;samples:Record<string,number>}
export interface State {courseGrades?:CourseGrade[];learningAnalytics?:LearningAnalysis[];user:User;users:User[];courses:Course[];classes:BaseClass[];teachingClasses:TeachingClass[];enrollments:{studentId:string;classId:string}[];tasks:CourseTask[];projects:Project[];notifications:Notice[];history:HistoryRecord[]}



