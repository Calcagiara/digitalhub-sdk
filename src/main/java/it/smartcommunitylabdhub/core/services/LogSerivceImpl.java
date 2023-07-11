package it.smartcommunitylabdhub.core.services;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import it.smartcommunitylabdhub.core.exceptions.CoreException;
import it.smartcommunitylabdhub.core.exceptions.CustomException;
import it.smartcommunitylabdhub.core.models.builders.dtos.LogDTOBuilder;
import it.smartcommunitylabdhub.core.models.builders.entities.LogEntityBuilder;
import it.smartcommunitylabdhub.core.models.dtos.LogDTO;
import it.smartcommunitylabdhub.core.models.entities.Log;
import it.smartcommunitylabdhub.core.repositories.LogRepository;
import it.smartcommunitylabdhub.core.services.interfaces.LogService;

@Service
public class LogSerivceImpl implements LogService {

    @Autowired
    LogRepository logRepository;

    @Override
    public List<LogDTO> getLogs(Pageable pageable) {
        try {
            Page<Log> logPage = this.logRepository.findAll(pageable);
            return logPage.getContent().stream()
                    .map(log -> new LogDTOBuilder(log).build())
                    .collect(Collectors.toList());

        } catch (CustomException e) {
            throw new CoreException(
                    "InternalServerError",
                    e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public LogDTO getLog(String uuid) {
        return logRepository.findById(uuid)
                .map(log -> {
                    try {
                        return new LogDTOBuilder(log).build();
                    } catch (CustomException e) {
                        throw new CoreException("InternalServerError", e.getMessage(),
                                HttpStatus.INTERNAL_SERVER_ERROR);
                    }
                })
                .orElseThrow(() -> new CoreException("LogNotFound", "The log you are searching for does not exist.",
                        HttpStatus.NOT_FOUND));

    }

    @Override
    public boolean deleteLog(String uuid) {
        try {
            this.logRepository.deleteById(uuid);
            return true;
        } catch (Exception e) {
            throw new CoreException(
                    "InternalServerError",
                    "cannot delete artifact",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public LogDTO createLog(LogDTO logDTO) {
        if (logDTO.getId() != null && logRepository.existsById(logDTO.getId())) {
            throw new CoreException("DuplicateLogId",
                    "Cannot create the log", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        Optional<Log> savedLog = Optional.ofNullable(logDTO)
                .map(LogEntityBuilder::new)
                .map(LogEntityBuilder::build)
                .map(this.logRepository::save);

        return savedLog.map(log -> new LogDTOBuilder(log).build())
                .orElseThrow(() -> new CoreException(
                        "InternalServerError",
                        "Error saving log",
                        HttpStatus.INTERNAL_SERVER_ERROR));
    }

    @Override
    public List<LogDTO> getLogsByRunUuid(String uuid) {
        return logRepository.findByRun(uuid)
                .stream()
                .map(log -> {
                    try {
                        return new LogDTOBuilder(log).build();
                    } catch (CustomException e) {
                        throw new CoreException("InternalServerError", e.getMessage(),
                                HttpStatus.INTERNAL_SERVER_ERROR);
                    }
                }).collect(Collectors.toList());
    }

}
