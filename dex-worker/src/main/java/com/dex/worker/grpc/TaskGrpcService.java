package com.dex.worker.grpc;

import com.dex.common.model.task.TaskConfig;
import com.dex.common.util.JsonUtil;
import com.dex.grpc.TaskProto;
import com.dex.grpc.TaskServiceGrpc;
import com.dex.worker.executor.TaskExecutorManager;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TaskGrpcService extends TaskServiceGrpc.TaskServiceImplBase {

    @Autowired
    private TaskExecutorManager executorManager;

    @Override
    public void execute(TaskProto.TaskRequest request, StreamObserver<TaskProto.TaskResponse> responseObserver) {
        log.info("gRPC 收到任务执行请求: taskId={}, instanceId={}", request.getTaskId(), request.getInstanceId());
        try {
            TaskConfig config = JsonUtil.fromJson(request.getConfigJson(), TaskConfig.class);
            if (config == null) {
                throw new RuntimeException("反序列化 TaskConfig 失败");
            }
            executorManager.submit(config, request.getInstanceId());
            TaskProto.TaskResponse response = TaskProto.TaskResponse.newBuilder()
                    .setAccepted(true)
                    .setMessage("Task accepted")
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("gRPC 执行任务失败", e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void stop(TaskProto.StopRequest request, StreamObserver<TaskProto.StopResponse> responseObserver) {
        log.info("gRPC 收到停止请求: taskId={}, instanceId={}", request.getTaskId(), request.getInstanceId());
        executorManager.stop(request.getTaskId(), request.getInstanceId());
        TaskProto.StopResponse response = TaskProto.StopResponse.newBuilder()
                .setStopped(true)
                .setMessage("Task stopped")
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}