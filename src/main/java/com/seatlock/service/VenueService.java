package com.seatlock.service;

import com.seatlock.dto.CreateVenueRequest;
import com.seatlock.dto.VenueResponse;
import com.seatlock.exception.NotFoundException;
import com.seatlock.model.Venue;
import com.seatlock.repository.VenueRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VenueService {

    private final VenueRepository venueRepository;

    public VenueService(VenueRepository venueRepository) {
        this.venueRepository = venueRepository;
    }

    @Transactional
    public VenueResponse createVenue(CreateVenueRequest request) {
        Venue venue = new Venue(request.name(), request.city());
        for (CreateVenueRequest.SectionRequest section : request.sections()) {
            venue.addSection(section.name(), section.rows(), section.seatsPerRow(), section.priceCents());
        }
        return VenueResponse.from(venueRepository.save(venue));
    }

    @Transactional(readOnly = true)
    public VenueResponse getVenue(Long id) {
        return VenueResponse.from(findVenue(id));
    }

    @Transactional(readOnly = true)
    public List<VenueResponse> listVenues() {
        return venueRepository.findAll().stream().map(VenueResponse::from).toList();
    }

    Venue findVenue(Long id) {
        return venueRepository.findById(id).orElseThrow(() -> new NotFoundException("Venue", id));
    }
}
