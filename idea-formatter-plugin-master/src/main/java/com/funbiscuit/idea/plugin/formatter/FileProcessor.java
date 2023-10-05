package com.funbiscuit.idea.plugin.formatter;

import com.funbiscuit.idea.plugin.formatter.report.FileInfo;
import com.intellij.psi.PsiFile;

public interface FileProcessor {
    void processFile(PsiFile originalFile, FileInfo fileInfo);
}
