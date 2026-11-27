pipeline {
    agent any

    environment {
        REGISTRY      = "100.123.181.208:5000"
        IMAGE_NAME    = "${REGISTRY}/skoolia/kantin-be"
        IMAGE_TAG     = "${BUILD_NUMBER}"
        K8S_NAMESPACE = "skoolia"
        K8S_DEPLOY    = "kantin-be"
        CONTAINER     = "kantin-be"
        // Pindai supply chain (issue #440) — versi dipatok agar reproducible.
        TRIVY_IMAGE   = "aquasec/trivy:0.76.2"
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timeout(time: 30, unit: 'MINUTES')
        disableConcurrentBuilds()
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                echo "Branch: ${env.GIT_BRANCH} | Commit: ${env.GIT_COMMIT?.take(7)}"
            }
        }

        stage('Test') {
            // Gerbang mutu (issue #141, WORKFLOW.md §4): `verify` menjalankan
            // unit test (Surefire, *Test) + integration test (Failsafe, *IT)
            // berbasis Testcontainers. Agent Jenkins sudah punya Docker (dipakai
            // build image di stage berikut), jadi IT ledger/race-condition
            // benar-benar dijalankan. Pipeline GAGAL bila ada test merah.
            steps {
                sh './mvnw -B -ntp verify'
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'target/*-reports/*.xml'
                }
            }
        }

        stage('Build Docker Image') {
            steps {
                sh """
                    docker build \
                        -t ${IMAGE_NAME}:${IMAGE_TAG} \
                        -t ${IMAGE_NAME}:latest \
                        -f Dockerfile .
                """
            }
        }

        stage('Pindai Supply Chain') {
            // issue #440 (OWASP A03:2025 Software Supply Chain Failures).
            // Dijalankan SETELAH image dibangun dan SEBELUM push, supaya image
            // dengan CVE HIGH/CRITICAL yang sudah ada patch tidak pernah masuk
            // registry. SBOM CycloneDX (target/bom.json) sudah dibuat oleh
            // stage Test (`./mvnw verify` → cyclonedx-maven-plugin).
            //
            // Darurat saja: jalankan build dengan TRIVY_SKIP=true untuk melewati
            // stage ini (mis. registry/Docker Hub tidak bisa dihubungi).
            when { expression { env.TRIVY_SKIP != 'true' } }
            steps {
                sh """
                    test -f target/bom.json || {
                        echo "target/bom.json tidak ada — cyclonedx-maven-plugin belum jalan"
                        exit 1
                    }

                    # Tanpa pipe: status keluar `docker run` langsung jadi status
                    # stage (tidak butuh pipefail, yang tidak ada di dash).
                    # Baseline utang lama: /src/.trivyignore.yaml (kedaluwarsa
                    # 2027-01-31, remediasi issue #455). Gate tetap gagal untuk
                    # CVE baru; --show-suppressed menjaga utang lama tetap tampak.
                    # 1) Dependency (dari SBOM hasil build Maven)
                    docker run --rm -v "\$PWD:/src" -w /src ${TRIVY_IMAGE} \\
                        sbom --scanners vuln \\
                             --severity HIGH,CRITICAL \\
                             --ignore-unfixed \\
                             --ignorefile /src/.trivyignore.yaml \\
                             --show-suppressed \\
                             --exit-code 1 \\
                             --format table \\
                             --output /src/laporan-trivy-dependency.txt \\
                             target/bom.json
                    cat laporan-trivy-dependency.txt

                    # 2) Image yang baru dibangun (sebelum push)
                    docker run --rm \\
                        -v /var/run/docker.sock:/var/run/docker.sock \\
                        -v "\$PWD:/src" -w /src ${TRIVY_IMAGE} \\
                        image --scanners vuln \\
                              --severity HIGH,CRITICAL \\
                              --ignore-unfixed \\
                              --ignorefile /src/.trivyignore.yaml \\
                              --show-suppressed \\
                              --exit-code 1 \\
                              --format table \\
                              --output /src/laporan-trivy-image.txt \\
                              ${IMAGE_NAME}:${IMAGE_TAG}
                    cat laporan-trivy-image.txt

                    # 3) SBOM image untuk arsip
                    docker run --rm \\
                        -v /var/run/docker.sock:/var/run/docker.sock \\
                        -v "\$PWD:/src" -w /src ${TRIVY_IMAGE} \\
                        image --scanners vuln --format cyclonedx \\
                              --output /src/sbom-image.cdx.json \\
                              ${IMAGE_NAME}:${IMAGE_TAG}
                """
            }
            post {
                always {
                    archiveArtifacts artifacts: 'target/bom.json, sbom-image.cdx.json, laporan-trivy-*.txt',
                            allowEmptyArchive: true
                }
            }
        }

        stage('Supply Chain DILEWATI (TRIVY_SKIP=true)') {
            // Visibilitas: kalau escape hatch dipakai, jangan lewat diam-diam.
            // Stage ini hanya muncul saat TRIVY_SKIP=true sehingga alasan
            // pelewatan tercatat di log build dan bisa ditanyakan saat review.
            when { expression { env.TRIVY_SKIP == 'true' } }
            steps {
                echo '''PERINGATAN: TRIVY_SKIP=true — pemindaian dependency/image DILEWATI.
Image akan di-push TANPA gate CVE. Hanya untuk darurat (mis. Docker Hub tidak
bisa dihubungi). Catat alasannya di deskripsi build/PR, lalu jalankan ulang
build normal secepatnya.'''
            }
        }

        stage('Push to Registry') {
            steps {
                sh """
                    docker push ${IMAGE_NAME}:${IMAGE_TAG}
                    docker push ${IMAGE_NAME}:latest
                """
            }
        }

        stage('Deploy to k3s') {
            steps {
                sh """
                    kubectl set image deployment/${K8S_DEPLOY} \
                        ${CONTAINER}=${IMAGE_NAME}:${IMAGE_TAG} \
                        -n ${K8S_NAMESPACE}

                    kubectl rollout status deployment/${K8S_DEPLOY} -n ${K8S_NAMESPACE} --timeout=600s
                """
            }
        }
    }

    post {
        success {
            echo "Deploy kantin-be:${IMAGE_TAG} berhasil."
        }
        failure {
            echo "Deploy gagal — rollback ke versi sebelumnya..."
            sh """
                kubectl rollout undo deployment/${K8S_DEPLOY} \
                    -n ${K8S_NAMESPACE} || true
            """
        }
        always {
            // Hapus image lokal untuk hemat disk Jenkins
            sh "docker rmi ${IMAGE_NAME}:${IMAGE_TAG} || true"
        }
    }
}
