package org.jetbrains.research.refactoringDemoPlugin

import com.intellij.psi.PsiClass
import com.intellij.psi.util.childrenOfType
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dataClumpRefactoring.*

class RefactoringTest  :  BasePlatformTestCase() {

    fun nop(){}
    fun testRefactoring() {
        val pointPsi=myFixture.configureByText("Point.java",
            """
        package org.jetbrains.research.refactoringDemoPlugin;
        public class Point {
            private int x;
            private int y;
            private int z;
            public Point(int x, int y, int z) {
                this.x = x;
                this.y = y;
                this.z = z;
            }
            public int getX() {
                return x;
            }
            public int getY() {
                return y;
            }
            public int getZ() {
                return z;
            }
            public void setX(int x) {
                this.x = x;
            }
                
            public void setY(int y) {
                this.y = y;
            }
            public void setZ(int z) {
                this.z = z;
            }
        }
        """.trimIndent()
        )
        val usagePsi=myFixture.configureByText("Library.java",
            """
        package org.jetbrains.research.refactoringDemoPlugin;
        public class Library {
            public void test(int x, int y, int z){
            System.out.println("x: "+x+" y: "+y+" z: "+z);
            }
            private void main(){
            test(1,2,3);
            }
        }
        """.trimIndent()
        )
        val refactorer=ManualDataClumpRefactorer(myFixture.project,PsiReferenceFinder(),ManualJavaClassCreator(
            mutableMapOf()
        ))
        val mainMethod=usagePsi.childrenOfType<PsiClass>()[0].methods[1]
        val pointClass=pointPsi.childrenOfType<PsiClass>()[0]

        val testMethod=usagePsi.childrenOfType<PsiClass>()[0].methods[0]

        refactorer.updateMethodSignature(usagePsi.project,testMethod,pointClass, arrayOf("x","y","z"),PrimitiveNameService(StubNameValidityChecker()))
        assert(testMethod.parameters.size==1)
        nop()


    }
}