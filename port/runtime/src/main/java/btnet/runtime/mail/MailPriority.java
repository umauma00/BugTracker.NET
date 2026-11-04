package btnet.runtime.mail;

/**
 * Stands for .NET {@code System.Net.Mail.MailPriority} (same order/values:
 * Normal = 0, Low = 1, High = 2). Always written fully qualified in translated
 * code ({@code btnet.runtime.mail.MailPriority}) because {@code btnet.Mail.MailPriority}
 * has the same simple name (§4, J-C15).
 */
public enum MailPriority {
    Normal,
    Low,
    High
}
